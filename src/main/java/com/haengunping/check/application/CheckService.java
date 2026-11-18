package com.haengunping.check.application;

import com.haengunping.check.CheckQuery;
import com.haengunping.check.domain.CheckResult;
import com.haengunping.check.domain.CheckRules;
import com.haengunping.check.domain.CheckWeek;
import com.haengunping.check.infra.CheckRepository;
import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import com.haengunping.mood.MoodQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주간 점검(CHK-01~06). 요청마다 시각을 한 번만 읽어 주차와 창을 같이 정한다(23:59:59에 들어온 요청이 중간에
 * 월요일로 넘어가도 주가 갈리지 않는다). 제출·닫기는 한 문장({@code ON CONFLICT DO NOTHING})이라 더블 클릭이나
 * 동시 요청에도 한 건만 들어간다.
 */
@Service
@Transactional(readOnly = true)
public class CheckService implements CheckQuery {

    private final CheckRepository checks;
    private final MoodQuery moods;
    private final Clock clock;

    CheckService(CheckRepository checks, MoodQuery moods, Clock clock) {
        this.checks = checks;
        this.moods = moods;
        this.clock = clock;
    }

    /** 이번 주 점검 상태(CHK-01). 기록이 없고 창이 열려 있을 때만 due. */
    public Current current(long userId) {
        CheckWeek week = CheckWeek.of(ZonedDateTime.now(clock));
        Optional<String> status = checks.statusOf(userId, week.weekStart());
        return new Current(
                week.weekStart(),
                status.isEmpty() && week.windowOpen(),
                status.filter(CheckRepository.DONE::equals).isPresent(),
                status.filter(CheckRepository.DISMISSED::equals).isPresent());
    }

    /**
     * 점검 제출(CHK-02~06). {@code answers}는 검증을 통과한 값이어야 한다.
     * 창이 닫혀 있거나 이번 주 기록이 이미 있으면 CHECK_NOT_DUE.
     */
    @Transactional
    public List<CheckResult> submit(long userId, List<Integer> answers) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        CheckWeek week = CheckWeek.of(now);
        if (!week.windowOpen()) {
            throw new ApiException(ErrorCode.CHECK_NOT_DUE);
        }
        List<CheckResult> results = CheckRules.evaluate(
                answers, () -> moods.lowMoodDaysInWeek(userId, week.weekStart()));
        if (checks.insertDone(userId, week.weekStart(), results, now.toInstant()) == 0) {
            throw new ApiException(ErrorCode.CHECK_NOT_DUE);
        }
        return results;
    }

    /** 이번 주 점검 닫기. 창과 무관하고, 이미 기록이 있으면 그대로 둔다(멱등). */
    @Transactional
    public void dismiss(long userId) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        checks.insertDismissed(userId, CheckWeek.of(now).weekStart(), now.toInstant());
    }

    @Override
    public boolean aboveThresholdSince(long userId, Instant since) {
        return checks.existsAboveThresholdSince(userId, since);
    }

    @Override
    public Set<Long> usersWithRecord(LocalDate weekStart, Collection<Long> userIds) {
        return checks.userIdsWithRecord(weekStart, userIds);
    }

    /**
     * @param weekStart 이번 주 월요일(KST)
     * @param due       지금 점검을 보여줄지
     */
    public record Current(LocalDate weekStart, boolean due, boolean done, boolean dismissed) {
    }
}
