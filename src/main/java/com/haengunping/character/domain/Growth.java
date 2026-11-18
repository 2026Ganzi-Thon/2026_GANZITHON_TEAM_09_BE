package com.haengunping.character.domain;

/**
 * 성장 수치 계산(CHR-02·05·06). 성장은 1/60 단위 정수로 다룬다.
 *
 * <p>하루 성장 2 × 완료 ÷ 구성원은 1.333…처럼 끝나지 않는 소수가 되어, 소수로 누적하면 100 도달 판정이 어긋날 수 있다.
 * 구성원은 1~6명이고 120은 1~6으로 모두 나누어떨어지므로 {@code 120 × 완료 ÷ 구성원}은 항상 정확한 정수다.
 * 100 = {@value #FULL} 단위. SQL 합계도 같은 식을 쓴다(DailyGroupProgressRepository).
 */
public final class Growth {

    public static final int UNITS_PER_POINT = 60;
    public static final int FULL = 100 * UNITS_PER_POINT;
    private static final int STAGE_SIZE = 25 * UNITS_PER_POINT;
    public static final int MAX_STAGE = 4;

    private Growth() {
    }

    /** 하루 성장(단위). 2 × 완료 ÷ 구성원 = 120 × 완료 ÷ 구성원 / 60. 구성원 0명이면 0. */
    public static int dailyUnits(int completed, int members) {
        if (members < 0 || members > 6 || completed < 0 || completed > members) {
            throw new IllegalArgumentException("구성원 " + members + "명 중 완료 " + completed + "명");
        }
        return members == 0 ? 0 : 2 * UNITS_PER_POINT * completed / members;
    }

    /** 화면 표시 단위 = 확정 합 + 오늘 임시, 최대 100(CHR-05). */
    public static int displayUnits(long confirmedUnits, int todayUnits) {
        return (int) Math.min(FULL, confirmedUnits + todayUnits);
    }

    /** 표시 수치(소수 첫째 자리, 버림). 반올림하면 49.96이 50.0으로 보이는데 단계는 2단계라 어긋나서 버린다. */
    public static double progress(int displayUnits) {
        return displayUnits / (UNITS_PER_POINT / 10) / 10.0;
    }

    /** 단계 1~4(CHR-06). 25 미만 1단계 … 75 이상 4단계. 100(완성)도 4단계로 두고 완성은 따로 판정한다. */
    public static int stage(int displayUnits) {
        return Math.min(MAX_STAGE, displayUnits / STAGE_SIZE + 1);
    }

    /** 확정 합이 100 이상이면 완성(CHR-07). */
    public static boolean isComplete(long confirmedUnits) {
        return confirmedUnits >= FULL;
    }
}
