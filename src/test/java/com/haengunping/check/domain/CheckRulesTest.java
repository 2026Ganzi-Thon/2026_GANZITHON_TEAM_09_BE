package com.haengunping.check.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** prd.md 7절 주간 점검 표 4줄과 CHK-02·03·04·06. */
class CheckRulesTest {

    private static List<Integer> a(Integer... values) {
        return Arrays.asList(values);
    }

    @Test
    void CHK03_우울만_높으면_DEPRESSION() {
        assertThat(CheckRules.evaluate(a(2, 1, 0, 0), () -> 0)).containsExactly(CheckResult.DEPRESSION);
    }

    @Test
    void CHK03_둘_다_높으면_둘_다() {
        assertThat(CheckRules.evaluate(a(2, 1, 3, 0), () -> 0))
                .containsExactly(CheckResult.DEPRESSION, CheckResult.ANXIETY);
    }

    @Test
    void CHK03_불안만_높으면_ANXIETY() {
        assertThat(CheckRules.evaluate(a(0, 0, 1, 2), () -> 0)).containsExactly(CheckResult.ANXIETY);
    }

    @Test
    void CHK03_둘_다_미만이고_낮은_기분_4일이면_LOW_MOOD_DAYS() {
        assertThat(CheckRules.evaluate(a(1, 1, 1, 1), () -> 4)).containsExactly(CheckResult.LOW_MOOD_DAYS);
    }

    @Test
    void CHK03_낮은_기분_3일이면_STABLE() {
        assertThat(CheckRules.evaluate(a(1, 1, 1, 1), () -> 3)).containsExactly(CheckResult.STABLE);
    }

    @Test
    void CHK03_경계_합이_2면_미만_3이면_이상() {
        assertThat(CheckRules.evaluate(a(1, 1, 0, 0), () -> 0)).containsExactly(CheckResult.STABLE);
        assertThat(CheckRules.evaluate(a(3, 0, 0, 0), () -> 0)).containsExactly(CheckResult.DEPRESSION);
        assertThat(CheckRules.evaluate(a(0, 0, 3, 0), () -> 0)).containsExactly(CheckResult.ANXIETY);
        assertThat(CheckRules.evaluate(a(3, 3, 3, 3), () -> 0))
                .containsExactly(CheckResult.DEPRESSION, CheckResult.ANXIETY);
    }

    @Test
    void CHK03_낮은_기분_일수는_필요할_때만_묻는다() {
        AtomicInteger asked = new AtomicInteger();

        CheckRules.evaluate(a(2, 1, 0, 0), asked::incrementAndGet); // 우울이면 묻지 않는다
        CheckRules.evaluate(a(0, 0, 2, 1), asked::incrementAndGet); // 불안이면 묻지 않는다
        assertThat(asked).hasValue(0);

        CheckRules.evaluate(a(0, 0, 0, 0), asked::incrementAndGet);
        assertThat(asked).hasValue(1);
    }

    @Test
    void CHK03_우울이_있으면_낮은_기분이_많아도_LOW_MOOD_DAYS를_더하지_않는다() {
        assertThat(CheckRules.evaluate(a(2, 1, 0, 0), () -> 7)).containsExactly(CheckResult.DEPRESSION);
    }

    @Test
    void CHK02_문항_4개_각_0에서_3_정수만_통과한다() {
        assertThat(CheckRules.invalidFields(a(0, 1, 2, 3))).isEmpty();
        assertThat(CheckRules.invalidFields(a(3, 3, 3, 3))).isEmpty();
        assertThat(CheckRules.invalidFields(a(0, 0, 0, 0))).isEmpty();
    }

    @Test
    void CHK02_개수가_다르거나_없으면_answers() {
        assertThat(CheckRules.invalidFields(null)).containsExactly("answers");
        assertThat(CheckRules.invalidFields(a())).containsExactly("answers");
        assertThat(CheckRules.invalidFields(a(0, 0, 0))).containsExactly("answers");
        assertThat(CheckRules.invalidFields(a(0, 0, 0, 0, 0))).containsExactly("answers");
    }

    @Test
    void CHK02_범위_밖이나_빈_값은_그_자리를_알려준다() {
        assertThat(CheckRules.invalidFields(a(0, 0, 0, 4))).containsExactly("answers[3]");
        assertThat(CheckRules.invalidFields(a(-1, 0, 0, 0))).containsExactly("answers[0]");
        assertThat(CheckRules.invalidFields(a(0, null, 9, 0))).containsExactly("answers[1]", "answers[2]");
    }

    @Test
    void 검증을_통과하지_않은_답은_판정하지_않는다() {
        assertThatThrownBy(() -> CheckRules.evaluate(a(0, 0, 0, 4), () -> 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckRules.evaluate(null, () -> 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void CHK06_결과별_도움받기_구역() {
        assertThat(CheckRules.sections(List.of(CheckResult.DEPRESSION)))
                .containsExactly(CheckSection.CRISIS_24H, CheckSection.COUNSELING);
        assertThat(CheckRules.sections(List.of(CheckResult.ANXIETY)))
                .containsExactly(CheckSection.CRISIS_24H, CheckSection.COUNSELING);
        assertThat(CheckRules.sections(List.of(CheckResult.DEPRESSION, CheckResult.ANXIETY)))
                .containsExactly(CheckSection.CRISIS_24H, CheckSection.COUNSELING);
        assertThat(CheckRules.sections(List.of(CheckResult.LOW_MOOD_DAYS))).containsExactly(CheckSection.DROP_IN);
        assertThat(CheckRules.sections(List.of(CheckResult.STABLE))).isEmpty();
    }

    @Test
    void CHK06_결과_문장은_기획서_표와_같다() {
        assertThat(CheckResult.DEPRESSION.message()).isEqualTo("요즘 많이 지쳐 있었던 것 같아요");
        assertThat(CheckResult.ANXIETY.message()).isEqualTo("걱정이 많았던 한 주였던 것 같아요");
        assertThat(CheckResult.LOW_MOOD_DAYS.message()).isEqualTo("기분이 가라앉은 날이 많았네요");
        assertThat(CheckResult.STABLE.message()).isEqualTo("이번 주는 크게 흔들리지 않았던 것 같아요");
        assertThat(CheckRules.NOTICE).isEqualTo("진단이 아니라 참고예요");
    }

    @Test
    void 결과_목록은_바꿀_수_없다() {
        List<CheckResult> results = CheckRules.evaluate(a(2, 1, 0, 0), () -> 0);
        assertThatThrownBy(() -> results.add(CheckResult.STABLE)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> CheckRules.sections(results).add(CheckSection.DROP_IN))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
