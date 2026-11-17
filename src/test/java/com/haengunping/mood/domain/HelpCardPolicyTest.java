package com.haengunping.mood.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HelpCardPolicyTest {

    private static final Instant DISMISSED = Instant.parse("2026-11-16T03:00:00Z");

    @Test
    void MOOD04_연속_구간은_오늘_포함_3일이다() {
        assertThat(HelpCardPolicy.streakStart(LocalDate.of(2026, 11, 16))).isEqualTo(LocalDate.of(2026, 11, 14));
    }

    @Test
    void MOOD04_3일_모두_낮은_기분일_때만_연속이다() {
        assertThat(HelpCardPolicy.isLowStreak(3)).isTrue();
        assertThat(HelpCardPolicy.isLowStreak(2)).isFalse();
        assertThat(HelpCardPolicy.isLowStreak(0)).isFalse();
    }

    @Test
    void MOOD04_닫은_뒤_7일이_되기_직전까지_숨긴다() {
        assertThat(HelpCardPolicy.isHidden(DISMISSED, DISMISSED)).isTrue();
        assertThat(HelpCardPolicy.isHidden(DISMISSED, DISMISSED.plus(Duration.ofDays(7)).minusSeconds(1))).isTrue();
    }

    @Test
    void MOOD04_닫은_뒤_7일이_지나면_다시_보인다() {
        assertThat(HelpCardPolicy.isHidden(DISMISSED, DISMISSED.plus(Duration.ofDays(7)))).isFalse();
    }
}
