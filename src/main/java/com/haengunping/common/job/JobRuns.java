package com.haengunping.common.job;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 날짜 단위 예약 작업을 한 번만 실행하게 하는 공용 장치(D-07, NFR-03, CHR-03). 테이블은 {@code job_runs}.
 *
 * <p>쓰는 법(담당 B의 자정 확정 예):
 * <pre>{@code
 * LocalDate yesterday = LocalDate.now(clock).minusDays(1);
 * jobRuns.runPending(JobName.FINALIZE_PROGRESS, firstDay, yesterday, day -> progressService.finalize(day));
 * }</pre>
 *
 * <p>동작 규칙:
 * <ul>
 *   <li>실행 권한은 {@code INSERT … ON CONFLICT} 한 문장으로 가져간다. 동시에 여러 번 불러도(예약 실행과 시연 시계가 겹쳐도)
 *       한 곳만 실행한다. 이 문장이 바로 커밋되므로 다른 호출은 실행 중인 것을 본다.</li>
 *   <li>작업 본문({@code work})은 이 클래스의 트랜잭션 밖에서 돈다. 본문이 필요하면 스스로 트랜잭션을 연다.</li>
 *   <li>본문이 성공한 뒤 DONE 기록 전에 서버가 죽으면 그 날짜는 다시 실행된다. 그래서 <b>본문은 멱등이어야 한다</b>(NFR-04).</li>
 *   <li>RUNNING인 채로 {@code haengunping.jobs.stale-after}(기본 1시간)가 지나면 죽은 실행으로 보고 다시 가져갈 수 있다.</li>
 *   <li>기록 시각({@code started_at}·{@code finished_at})은 DB의 {@code now()}를 쓴다. 시연 시계와 상관없는 운영 기록이다.</li>
 * </ul>
 */
@Component
public class JobRuns {

    private static final Logger log = LoggerFactory.getLogger(JobRuns.class);

    private final JdbcClient jdbc;
    private final Duration staleAfter;

    JobRuns(JdbcClient jdbc, @Value("${haengunping.jobs.stale-after:PT1H}") Duration staleAfter) {
        this.jdbc = jdbc;
        this.staleAfter = staleAfter;
    }

    /**
     * {@code runKey} 날짜의 작업을 아직 끝내지 않았으면 실행한다.
     * 예외는 밖으로 던지지 않고 FAILED로 기록·로그한 뒤 {@link RunOutcome#FAILED}를 돌려준다.
     */
    public RunOutcome runOnce(JobName job, LocalDate runKey, Runnable work) {
        Optional<OffsetDateTime> claimedAt = claim(job, runKey);
        if (claimedAt.isEmpty()) {
            return RunOutcome.SKIPPED;
        }
        try {
            work.run();
        } catch (RuntimeException e) {
            finish(job, runKey, claimedAt.get(), "FAILED");
            log.error("예약 작업 실패: {} {}", job, runKey, e);
            return RunOutcome.FAILED;
        }
        finish(job, runKey, claimedAt.get(), "DONE");
        return RunOutcome.RAN;
    }

    /** {@code from}~{@code to}(둘 다 포함) 중 아직 DONE이 아닌 날짜, 오래된 순. */
    public List<LocalDate> pendingDates(JobName job, LocalDate from, LocalDate to) {
        Set<LocalDate> done = new HashSet<>(jdbc.sql("""
                        SELECT run_key FROM job_runs
                        WHERE job_name = :job AND status = 'DONE' AND run_key BETWEEN :from AND :to
                        """)
                .param("job", job.name())
                .param("from", from)
                .param("to", to)
                .query(LocalDate.class)
                .list());
        return from.datesUntil(to.plusDays(1)).filter(day -> !done.contains(day)).toList();
    }

    /**
     * 밀린 날짜를 오래된 순서로 실행한다(CHR-03). 성장은 날짜 순서대로 쌓여야 하므로,
     * 한 날짜가 실패하거나 다른 곳에서 실행 중이면 그 뒤 날짜는 실행하지 않고 멈춘다.
     */
    public CatchUpResult runPending(JobName job, LocalDate from, LocalDate to, Consumer<LocalDate> work) {
        List<LocalDate> ran = new ArrayList<>();
        for (LocalDate day : pendingDates(job, from, to)) {
            RunOutcome outcome = runOnce(job, day, () -> work.accept(day));
            if (outcome != RunOutcome.RAN) {
                return new CatchUpResult(ran, day);
            }
            ran.add(day);
        }
        return new CatchUpResult(ran, null);
    }

    /** 실행 권한을 가져오면 이번 실행의 표식(started_at)을 돌려준다. */
    private Optional<OffsetDateTime> claim(JobName job, LocalDate runKey) {
        return jdbc.sql("""
                        INSERT INTO job_runs (job_name, run_key, status, started_at)
                        VALUES (:job, :runKey, 'RUNNING', now())
                        ON CONFLICT (job_name, run_key) DO UPDATE
                            SET status = 'RUNNING', started_at = now(), finished_at = NULL
                            WHERE job_runs.status = 'FAILED'
                               OR (job_runs.status = 'RUNNING'
                                   AND job_runs.started_at < now() - make_interval(secs => :staleSeconds))
                        RETURNING started_at
                        """)
                .param("job", job.name())
                .param("runKey", runKey)
                .param("staleSeconds", (double) staleAfter.toSeconds())
                .query(OffsetDateTime.class)
                .optional();
    }

    /**
     * 자기 실행만 끝낸다. 오래 걸려 다른 호출이 권한을 다시 가져갔다면 started_at이 달라 아무것도 바꾸지 않는다.
     */
    private void finish(JobName job, LocalDate runKey, OffsetDateTime claimedAt, String status) {
        int updated = jdbc.sql("""
                        UPDATE job_runs SET status = :status, finished_at = now()
                        WHERE job_name = :job AND run_key = :runKey AND status = 'RUNNING' AND started_at = :claimedAt
                        """)
                .param("status", status)
                .param("job", job.name())
                .param("runKey", runKey)
                .param("claimedAt", claimedAt)
                .update();
        if (updated == 0) {
            log.warn("예약 작업 기록을 갱신하지 못함(다른 실행이 가져감): {} {}", job, runKey);
        }
    }
}
