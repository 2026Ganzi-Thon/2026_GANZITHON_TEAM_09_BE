package com.haengunping.character.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GrowthTest {

    /** prd.md 6절 「캐릭터 하루 성장」 표. 단위는 1/60, 기대값은 표의 소수. */
    @ParameterizedTest(name = "{0}명 중 {1}명 → {2}")
    @CsvSource({
            "1, 1, 2.0",        // 1인 그룹
            "4, 3, 1.5",
            "5, 0, 0.0",
            "3, 2, 1.3333333",  // 완료한 1명이 22시에 나가 4명 → 3명
            "5, 4, 1.6"})       // 오늘 완료 후 20시에 들어온 1명 포함
    void CHR02_하루_성장은_2_곱하기_완료_나누기_구성원(int members, int completed, double expected) {
        int units = Growth.dailyUnits(completed, members);
        assertThat(units / (double) Growth.UNITS_PER_POINT).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void CHR02_구성원_범위_밖이면_계산하지_않는다() {
        assertThatThrownBy(() -> Growth.dailyUnits(4, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Growth.dailyUnits(0, 7)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Growth.dailyUnits(0, 0)).isZero();
    }

    @Test
    void CHR04_1_333씩_75일이면_소수_오차_없이_정확히_100이다() {
        long sum = 0;
        double naive = 0;
        for (int day = 0; day < 75; day++) {
            sum += Growth.dailyUnits(2, 3);
            naive += 2.0 * 2 / 3;
        }
        assertThat(Growth.isComplete(sum)).isTrue();
        assertThat(naive).isLessThan(100.0); // 소수로 누적했다면 완성되지 않는다
    }

    @Test
    void CHR06_단계_경계값() {
        assertThat(Growth.stage(0)).isEqualTo(1);
        assertThat(Growth.stage(units(25) - 1)).isEqualTo(1);   // 24.98…
        assertThat(Growth.stage(units(25))).isEqualTo(2);
        assertThat(Growth.stage(units(50) - 1)).isEqualTo(2);
        assertThat(Growth.stage(3006)).isEqualTo(3);            // 50.1
        assertThat(Growth.stage(units(75))).isEqualTo(4);
        assertThat(Growth.stage(Growth.FULL)).isEqualTo(4);     // 100은 완성으로 따로 판정
    }

    @Test
    void CHR05_표시_수치는_확정_합_더하기_오늘_임시_최대_100() {
        assertThat(Growth.displayUnits(units(99), units(2))).isEqualTo(Growth.FULL);
        assertThat(Growth.progress(Growth.displayUnits(units(99), units(2)))).isEqualTo(100.0);
    }

    @Test
    void CHR05_표시_수치는_소수_첫째_자리에서_버린다() {
        assertThat(Growth.progress(Growth.dailyUnits(2, 3))).isEqualTo(1.3);
        assertThat(Growth.progress(units(50) - 1)).isEqualTo(49.9); // 49.98…은 50.0이 아니라 49.9(2단계와 맞춤)
    }

    @Test
    void DEMO03_확정_48_2에서_4명_완료면_49_8_2단계_5명_완료면_50_2_3단계() {
        long confirmed = 2892; // 48.2
        int fourOfFive = Growth.displayUnits(confirmed, Growth.dailyUnits(4, 5));
        int fiveOfFive = Growth.displayUnits(confirmed, Growth.dailyUnits(5, 5));

        assertThat(Growth.progress(fourOfFive)).isEqualTo(49.8);
        assertThat(Growth.stage(fourOfFive)).isEqualTo(2);
        assertThat(Growth.progress(fiveOfFive)).isEqualTo(50.2);
        assertThat(Growth.stage(fiveOfFive)).isEqualTo(3);
    }

    @Test
    void CHR07_확정_99에_2를_더하면_완성() {
        assertThat(Growth.isComplete(units(99))).isFalse();
        assertThat(Growth.isComplete(units(99) + Growth.dailyUnits(1, 1))).isTrue();
    }

    @Test
    void 확정_행은_0명인_날을_만들지_않고_성장을_돌려준다() {
        assertThat(new DailyGroupProgress(1, LocalDate.of(2026, 11, 16), 4, 3, Instant.EPOCH).units()).isEqualTo(90);
        assertThatThrownBy(() -> new DailyGroupProgress(1, LocalDate.of(2026, 11, 16), 0, 0, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void CHR01_캐릭터_종류는_목록_안에서_고른다() {
        assertThat(CharacterKind.random(new Random(1))).isIn((Object[]) CharacterKind.values());
    }

    private static int units(int points) {
        return points * Growth.UNITS_PER_POINT;
    }
}
