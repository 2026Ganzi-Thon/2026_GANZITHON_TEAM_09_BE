package com.haengunping.character.application;

import com.haengunping.character.domain.CharacterKind;
import com.haengunping.character.domain.GroupCharacter;
import com.haengunping.character.domain.Growth;
import com.haengunping.character.infra.DailyGroupProgressRepository;
import com.haengunping.character.infra.GroupCharacterRepository;
import com.haengunping.common.job.CatchUpResult;
import com.haengunping.common.job.JobName;
import com.haengunping.common.job.JobRuns;
import com.haengunping.common.time.ClockConfig;
import com.haengunping.group.GroupCleanupGate;
import com.haengunping.group.GroupQuery;
import com.haengunping.mission.MissionQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 00:00(KST) 전날 확정 처리(CHR-03·07).
 *
 * <p>그날 23:59:59 구성원 중 미션을 완료한 수를 정수로 저장하고(CHR-02·04), 확정 합이 100에 닿으면
 * 그 캐릭터를 완성하고 다음 날부터 새 캐릭터를 0에서 시작한다(넘친 만큼은 버린다).
 * 날짜마다 한 트랜잭션이고, {@link JobRuns}가 같은 날짜를 두 번 돌지 않게 하고 밀린 날짜를 오래된 순으로 돈다.
 */
@Service
public class ProgressFinalizer implements GroupCleanupGate {

    private static final Logger log = LoggerFactory.getLogger(ProgressFinalizer.class);

    private final GroupCharacterRepository characters;
    private final DailyGroupProgressRepository progress;
    private final GroupQuery groups;
    private final MissionQuery missions;
    private final JobRuns jobs;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ProgressFinalizer(GroupCharacterRepository characters, DailyGroupProgressRepository progress, GroupQuery groups,
            MissionQuery missions, JobRuns jobs, TransactionTemplate tx, Clock clock) {
        this.characters = characters;
        this.progress = progress;
        this.groups = groups;
        this.missions = missions;
        this.jobs = jobs;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void nightly() {
        CatchUpResult result = finalizeUpTo(LocalDate.now(clock).minusDays(1));
        if (!result.completed()) {
            log.warn("확정 처리가 {}에서 멈춤, 다음 실행에서 이어 한다", result.stoppedAt());
        }
    }

    /** 아직 확정하지 않은 날부터 {@code date}까지 오래된 순으로 확정한다. */
    public CatchUpResult finalizeUpTo(LocalDate date) {
        LocalDate from = characters.earliestStart();
        // 모든 캐릭터가 오늘 시작했으면 어제 < 시작일이라 runPending이 to < from 예외를 던진다
        if (from == null || date.isBefore(from)) {
            return new CatchUpResult(List.of(), null);
        }
        return jobs.runPending(JobName.FINALIZE_PROGRESS, from, date, day -> tx.executeWithoutResult(s -> finalize(day)));
    }

    /** A의 00:10 비활성 정리(GRP-12)는 전날 확정이 끝난 뒤에만 돈다(CHR-03). 확정할 캐릭터가 없던 날은 막지 않는다. */
    @Override
    public boolean mayRemoveInactive(LocalDate previousDate) {
        LocalDate from = characters.earliestStart();
        return from == null || previousDate.isBefore(from)
                || jobs.pendingDates(JobName.FINALIZE_PROGRESS, previousDate, previousDate).isEmpty();
    }

    private void finalize(LocalDate day) {
        // 23:59:59.999999 KST. DB 시각은 마이크로초까지라 나노초를 쓰면 자정으로 반올림될 수 있다
        Instant endOfDay = day.plusDays(1).atStartOfDay(ClockConfig.KST).toInstant().minus(1, ChronoUnit.MICROS);
        Instant now = clock.instant();
        for (GroupCharacter character : characters.findByCompletedOnIsNullAndStartedOnLessThanEqual(day)) {
            long groupId = character.groupId();
            Set<Long> members = new HashSet<>(groups.activeUserIdsOf(groupId, endOfDay));
            if (members.isEmpty()) {
                continue; // 구성원 0명인 날은 행을 만들지 않는다
            }
            int completed = missions.completedUserIds(members, day).size();
            progress.insertIfAbsent(groupId, day, members.size(), completed, now);
            if (Growth.isComplete(progress.confirmedUnitsSince(groupId, character.startedOn()))) {
                character.complete(day);
                characters.saveAndFlush(character); // 진행 중 1개 unique 때문에 새 캐릭터보다 먼저 반영
                characters.save(new GroupCharacter(groupId, CharacterKind.random(ThreadLocalRandom.current()), day.plusDays(1)));
            }
        }
    }
}
