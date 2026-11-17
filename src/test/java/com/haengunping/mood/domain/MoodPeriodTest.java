package com.haengunping.mood.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import com.haengunping.mood.Mood;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MoodPeriodTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);

    @Test
    void MOOD05_양_끝_포함_93일은_조회할_수_있다() {
        MoodPeriod period = new MoodPeriod(FROM, FROM.plusDays(92));
        assertThat(period.to()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void MOOD05_하루만_조회할_수_있다() {
        new MoodPeriod(FROM, FROM);
    }

    @Test
    void MOOD05_94일은_400() {
        assertThatThrownBy(() -> new MoodPeriod(FROM, FROM.plusDays(93)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void MOOD05_시작이_끝보다_늦으면_400() {
        assertThatThrownBy(() -> new MoodPeriod(FROM, FROM.minusDays(1)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 낮은_기분은_지쳐요_우울해요_외로워요다() {
        assertThat(Mood.values()).filteredOn(Mood::isLow).containsExactly(Mood.TIRED, Mood.DEPRESSED, Mood.LONELY);
    }
}
