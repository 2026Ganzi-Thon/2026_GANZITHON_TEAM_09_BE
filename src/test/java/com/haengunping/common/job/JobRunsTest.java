package com.haengunping.common.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.haengunping.support.IntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 예약 작업을 날짜마다 한 번만 실행하는지(NFR-03), 밀린 날짜를 오래된 순서로 따라잡는지(CHR-03) 확인한다. */
class JobRunsTest extends IntegrationTest {

    private static final JobName JOB = JobName.FINALIZE_PROGRESS;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @Autowired
    JobRuns jobRuns;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM job_runs").update();
    }

    @Test
    void NFR03_같은_날짜는_한_번만_실행한다() {
        AtomicInteger calls = new AtomicInteger();

        assertThat(jobRuns.runOnce(JOB, DAY, calls::incrementAndGet)).isEqualTo(RunOutcome.RAN);
        assertThat(jobRuns.runOnce(JOB, DAY, calls::incrementAndGet)).isEqualTo(RunOutcome.SKIPPED);

        assertThat(calls).hasValue(1);
        assertThat(statusOf(JOB, DAY)).isEqualTo("DONE");
    }

    @Test
    void NFR03_작업이_다르면_같은_날짜도_따로_실행한다() {
        AtomicInteger calls = new AtomicInteger();

        jobRuns.runOnce(JobName.FINALIZE_PROGRESS, DAY, calls::incrementAndGet);
        jobRuns.runOnce(JobName.REMOVE_INACTIVE, DAY, calls::incrementAndGet);

        assertThat(calls).hasValue(2);
    }

    @Test
    void 실패하면_FAILED로_남고_다음_실행에서_다시_한다() {
        RunOutcome first = jobRuns.runOnce(JOB, DAY, () -> {
            throw new IllegalStateException("일부러 실패");
        });
        assertThat(first).isEqualTo(RunOutcome.FAILED);
        assertThat(statusOf(JOB, DAY)).isEqualTo("FAILED");

        AtomicInteger calls = new AtomicInteger();
        assertThat(jobRuns.runOnce(JOB, DAY, calls::incrementAndGet)).isEqualTo(RunOutcome.RAN);
        assertThat(calls).hasValue(1);
        assertThat(statusOf(JOB, DAY)).isEqualTo("DONE");
    }

    @Test
    void NFR03_실행_중인_날짜를_또_부르면_건너뛴다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<RunOutcome> first = executor.submit(() -> jobRuns.runOnce(JOB, DAY, () -> {
                calls.incrementAndGet();
                started.countDown();
                await(release);
            }));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

            RunOutcome second = jobRuns.runOnce(JOB, DAY, calls::incrementAndGet);
            release.countDown();

            assertThat(second).isEqualTo(RunOutcome.SKIPPED);
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(RunOutcome.RAN);
            assertThat(calls).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void NFR03_여러_스레드가_동시에_불러도_한_번만_실행한다() throws Exception {
        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger calls = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<RunOutcome>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return jobRuns.runOnce(JOB, DAY, calls::incrementAndGet);
                }));
            }
            int ran = 0;
            for (Future<RunOutcome> result : results) {
                if (result.get(10, TimeUnit.SECONDS) == RunOutcome.RAN) {
                    ran++;
                }
            }

            assertThat(ran).isEqualTo(1);
            assertThat(calls).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 서버가_죽어_오래_남은_RUNNING은_다시_실행한다() {
        jdbc.sql("""
                INSERT INTO job_runs (job_name, run_key, status, started_at)
                VALUES (:job, :day, 'RUNNING', now() - interval '2 hours')
                """)
                .param("job", JOB.name()).param("day", DAY).update();
        AtomicInteger calls = new AtomicInteger();

        assertThat(jobRuns.runOnce(JOB, DAY, calls::incrementAndGet)).isEqualTo(RunOutcome.RAN);
        assertThat(calls).hasValue(1);
    }

    @Test
    void 오래_걸린_실행은_다른_실행이_가져간_기록을_덮어쓰지_않는다() throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        CountDownLatch takeoverStarted = new CountDownLatch(1);
        CountDownLatch releaseTakeover = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RunOutcome> slow = executor.submit(() -> jobRuns.runOnce(JOB, DAY, () -> {
                slowStarted.countDown();
                await(releaseSlow);
            }));
            assertThat(slowStarted.await(10, TimeUnit.SECONDS)).isTrue();
            // 느린 실행이 2시간째 도는 상황을 만든다.
            jdbc.sql("UPDATE job_runs SET started_at = now() - interval '2 hours' WHERE job_name = :job AND run_key = :day")
                    .param("job", JOB.name()).param("day", DAY).update();

            // 다른 실행이 권한을 다시 가져가 도는 동안
            Future<RunOutcome> takeover = executor.submit(() -> jobRuns.runOnce(JOB, DAY, () -> {
                takeoverStarted.countDown();
                await(releaseTakeover);
                throw new IllegalStateException("일부러 실패");
            }));
            assertThat(takeoverStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // 느린 실행이 먼저 끝난다. 이 실행은 남의 기록을 DONE으로 바꾸면 안 된다.
            releaseSlow.countDown();
            slow.get(10, TimeUnit.SECONDS);
            assertThat(statusOf(JOB, DAY)).isEqualTo("RUNNING");

            releaseTakeover.countDown();
            assertThat(takeover.get(10, TimeUnit.SECONDS)).isEqualTo(RunOutcome.FAILED);
            assertThat(statusOf(JOB, DAY)).isEqualTo("FAILED");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 방금_시작한_RUNNING은_건드리지_않는다() {
        jdbc.sql("""
                INSERT INTO job_runs (job_name, run_key, status, started_at)
                VALUES (:job, :day, 'RUNNING', now())
                """)
                .param("job", JOB.name()).param("day", DAY).update();
        AtomicInteger calls = new AtomicInteger();

        assertThat(jobRuns.runOnce(JOB, DAY, calls::incrementAndGet)).isEqualTo(RunOutcome.SKIPPED);
        assertThat(calls).hasValue(0);
    }

    @Test
    void CHR03_밀린_날짜는_DONE이_아닌_날짜다() {
        LocalDate d1 = DAY;
        LocalDate d2 = DAY.plusDays(1);
        LocalDate d3 = DAY.plusDays(2);
        jobRuns.runOnce(JOB, d1, () -> { });
        jobRuns.runOnce(JOB, d2, () -> {
            throw new IllegalStateException("일부러 실패");
        });

        assertThat(jobRuns.pendingDates(JOB, d1, d3)).containsExactly(d2, d3);
    }

    @Test
    void CHR03_밀린_날짜를_오래된_순서로_처리하고_실패한_날에서_멈춘다() {
        LocalDate d1 = DAY;
        LocalDate d2 = DAY.plusDays(1);
        LocalDate d3 = DAY.plusDays(2);
        LocalDate d4 = DAY.plusDays(3);
        jobRuns.runOnce(JOB, d2, () -> { });
        List<LocalDate> called = new ArrayList<>();

        CatchUpResult result = jobRuns.runPending(JOB, d1, d4, day -> {
            called.add(day);
            if (day.equals(d3)) {
                throw new IllegalStateException("일부러 실패");
            }
        });

        assertThat(called).containsExactly(d1, d3);
        assertThat(result.ran()).containsExactly(d1);
        assertThat(result.stoppedAt()).isEqualTo(d3);
        assertThat(result.completed()).isFalse();

        CatchUpResult retry = jobRuns.runPending(JOB, d1, d4, day -> { });
        assertThat(retry.ran()).containsExactly(d3, d4);
        assertThat(retry.completed()).isTrue();
        assertThat(jobRuns.pendingDates(JOB, d1, d4)).isEmpty();
    }

    private String statusOf(JobName job, LocalDate day) {
        return jdbc.sql("SELECT status FROM job_runs WHERE job_name = :job AND run_key = :day")
                .param("job", job.name()).param("day", day)
                .query(String.class).single();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
