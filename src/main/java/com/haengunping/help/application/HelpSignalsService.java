package com.haengunping.help.application;

import com.haengunping.check.CheckQuery;
import com.haengunping.mood.MoodQuery;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;

/**
 * 도움받기 탭의 순서 신호(HELP-06). 순서 규칙은 기기에서 적용하고 서버는 참/거짓 두 개만 준다.
 * 신호 값은 로그에 남기지 않는다(SEC-11).
 */
@Service
public class HelpSignalsService {

    private final MoodQuery moods;
    private final CheckQuery checks;
    private final Clock clock;

    HelpSignalsService(MoodQuery moods, CheckQuery checks, Clock clock) {
        this.moods = moods;
        this.checks = checks;
        this.clock = clock;
    }

    public record Signals(boolean lonely3Days, boolean checkAboveThreshold) {
        static final Signals NONE = new Signals(false, false);
    }

    /** 사용자가 없으면(쿠키 없음·무효) 둘 다 false. 요청마다 시각을 한 번만 읽는다. */
    public Signals signalsFor(OptionalLong userId) {
        if (userId.isEmpty()) {
            return Signals.NONE;
        }
        ZonedDateTime now = ZonedDateTime.now(clock);
        return new Signals(
                moods.lonelyThreeDaysUpTo(userId.getAsLong(), now.toLocalDate()),
                checks.aboveThresholdSince(userId.getAsLong(), now.minus(Duration.ofDays(7)).toInstant()));
    }
}
