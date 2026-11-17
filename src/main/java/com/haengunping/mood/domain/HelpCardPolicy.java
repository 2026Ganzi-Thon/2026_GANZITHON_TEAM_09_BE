package com.haengunping.mood.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** 가벼운 도움 카드 규칙(MOOD-04). */
public final class HelpCardPolicy {

    /** 오늘 포함 연속으로 낮은 기분이어야 하는 날 수. */
    public static final int STREAK_DAYS = 3;
    public static final Duration HIDE_AFTER_DISMISS = Duration.ofDays(7);

    private HelpCardPolicy() {
    }

    /** 연속 판정 구간의 첫날. 오늘이 11/16이면 11/14. */
    public static LocalDate streakStart(LocalDate today) {
        return today.minusDays(STREAK_DAYS - 1);
    }

    /**
     * 구간 안 낮은 기분 날 수로 연속 여부를 판정한다. 기분은 하루 1개(PK)라
     * 구간의 모든 날이 낮은 기분이어야 개수가 STREAK_DAYS가 된다. 입력 안 한 날이 있으면 끊긴다.
     */
    public static boolean isLowStreak(int lowMoodDaysInStreak) {
        return lowMoodDaysInStreak == STREAK_DAYS;
    }

    /** 닫은 시각으로부터 7일이 지나기 전이면 숨긴다. */
    public static boolean isHidden(Instant dismissedAt, Instant now) {
        return now.isBefore(dismissedAt.plus(HIDE_AFTER_DISMISS));
    }
}
