package com.haengunping.check.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 이번 주(월~일)와 점검 창(CHK-01). 요청마다 시각을 한 번만 읽어 여기서 주차와 창을 같이 계산한다.
 * 그래서 일요일 23:59:59에 들어온 요청이 중간에 월요일로 넘어가 주가 갈리지 않는다.
 *
 * @param weekStart  이번 주 월요일(KST 날짜)
 * @param windowOpen 일요일 18:00~23:59(KST)인지
 */
public record CheckWeek(LocalDate weekStart, boolean windowOpen) {

    static final int WINDOW_START_HOUR = 18;

    /** {@code now}의 시간대는 KST(앱 시계)여야 한다. */
    public static CheckWeek of(ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        boolean open = now.getDayOfWeek() == DayOfWeek.SUNDAY && now.getHour() >= WINDOW_START_HOUR;
        return new CheckWeek(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), open);
    }
}
