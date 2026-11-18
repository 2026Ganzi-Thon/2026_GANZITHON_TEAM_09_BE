package com.haengunping.check.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * 주간 점검의 규칙 계산(CHK-02·03·06). 시간·DB·외부 호출 없는 순수 함수다.
 * 점수는 결과 코드로만 바꿔 쓰고 어디에도 담지 않는다(CHK-04).
 */
public final class CheckRules {

    /** 결과 화면 고정 문구. */
    public static final String NOTICE = "진단이 아니라 참고예요";

    static final int QUESTIONS = 4;
    static final int MAX_ANSWER = 3;
    /** 우울(1·2번)·불안(3·4번) 각각의 합이 이 값 이상이면 해당 결과. */
    static final int THRESHOLD = 3;
    /** 이번 주 낮은 기분이 이 일수 이상이면 LOW_MOOD_DAYS. */
    static final int LOW_MOOD_DAYS_THRESHOLD = 4;

    private CheckRules() {
    }

    /**
     * 답이 어긋난 칸 이름(CHK-02). 없으면 빈 목록이다. 문항 4개, 각 0~3 정수여야 한다.
     * 개수가 다르거나 목록이 없으면 {@code answers}, 값이 비었거나 범위 밖이면 {@code answers[i]}.
     */
    public static List<String> invalidFields(List<Integer> answers) {
        if (answers == null || answers.size() != QUESTIONS) {
            return List.of("answers");
        }
        List<String> invalid = new ArrayList<>();
        for (int i = 0; i < answers.size(); i++) {
            Integer a = answers.get(i);
            if (a == null || a < 0 || a > MAX_ANSWER) {
                invalid.add("answers[" + i + "]");
            }
        }
        return invalid;
    }

    /**
     * 결과 판정(CHK-03), 위에서부터: 우울 합 ≥ 3 → DEPRESSION, 불안 합 ≥ 3 → ANXIETY(둘 다면 둘 다).
     * 둘 다 아닐 때만 이번 주 낮은 기분이 4일 이상이면 LOW_MOOD_DAYS, 아니면 STABLE.
     * 낮은 기분 일수는 필요할 때만 묻는다(조회 비용을 아끼고, 우울·불안이 있으면 쓰지 않는다).
     *
     * @param answers 우울1, 우울2, 불안1, 불안2. 검증을 통과한 값이어야 한다
     */
    public static List<CheckResult> evaluate(List<Integer> answers, IntSupplier lowMoodDaysInWeek) {
        if (!invalidFields(answers).isEmpty()) {
            throw new IllegalArgumentException("검증을 통과하지 않은 답");
        }
        List<CheckResult> results = new ArrayList<>();
        if (answers.get(0) + answers.get(1) >= THRESHOLD) {
            results.add(CheckResult.DEPRESSION);
        }
        if (answers.get(2) + answers.get(3) >= THRESHOLD) {
            results.add(CheckResult.ANXIETY);
        }
        if (!results.isEmpty()) {
            return List.copyOf(results);
        }
        return List.of(lowMoodDaysInWeek.getAsInt() >= LOW_MOOD_DAYS_THRESHOLD
                ? CheckResult.LOW_MOOD_DAYS
                : CheckResult.STABLE);
    }

    /** 결과 화면에서 보여줄 도움받기 구역(CHK-06). */
    public static List<CheckSection> sections(List<CheckResult> results) {
        if (results.contains(CheckResult.DEPRESSION) || results.contains(CheckResult.ANXIETY)) {
            return List.of(CheckSection.CRISIS_24H, CheckSection.COUNSELING);
        }
        if (results.contains(CheckResult.LOW_MOOD_DAYS)) {
            return List.of(CheckSection.DROP_IN);
        }
        return List.of();
    }
}
