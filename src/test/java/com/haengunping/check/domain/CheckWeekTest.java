package com.haengunping.check.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** CHK-01: 일요일 18:00~23:59(KST)에만 창이 열리고, 주는 월요일에 시작한다. 2026-11-22는 일요일. */
class CheckWeekTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private static CheckWeek at(String dateTime) {
        return CheckWeek.of(ZonedDateTime.of(java.time.LocalDateTime.parse(dateTime), KST));
    }

    @Test
    void CHK01_일요일_17시59분은_닫혀_있다() {
        assertThat(at("2026-11-22T17:59:59").windowOpen()).isFalse();
    }

    @Test
    void CHK01_일요일_18시정각부터_열린다() {
        assertThat(at("2026-11-22T18:00:00").windowOpen()).isTrue();
    }

    @Test
    void CHK01_일요일_23시59분59초까지_열려_있다() {
        assertThat(at("2026-11-22T23:59:59").windowOpen()).isTrue();
    }

    @Test
    void CHK01_월요일_0시는_닫히고_새_주가_시작된다() {
        CheckWeek monday = at("2026-11-23T00:00:00");

        assertThat(monday.windowOpen()).isFalse();
        assertThat(monday.weekStart()).isEqualTo(LocalDate.of(2026, 11, 23));
    }

    @Test
    void CHK01_일요일이_아니면_저녁이어도_닫혀_있다() {
        assertThat(at("2026-11-21T19:00:00").windowOpen()).isFalse(); // 토
        assertThat(at("2026-11-16T20:00:00").windowOpen()).isFalse(); // 월
    }

    @Test
    void 주는_월요일부터_일요일까지다() {
        for (int day = 16; day <= 22; day++) {
            assertThat(at("2026-11-" + day + "T12:00:00").weekStart()).as("11/" + day).isEqualTo(MONDAY);
        }
    }

    @Test
    void 일요일_밤도_그_주에_속한다() {
        assertThat(at("2026-11-22T23:59:59").weekStart()).isEqualTo(MONDAY);
    }

    @Test
    void 시간대가_KST가_아니면_그_시간대의_날짜로_계산한다() {
        // 같은 순간이라도 UTC로 읽으면 일요일 09:00이라 창이 닫힌다 — 호출하는 쪽이 앱 시계(KST)를 써야 하는 이유
        ZonedDateTime utc = ZonedDateTime.of(2026, 11, 22, 9, 0, 0, 0, ZoneId.of("UTC"));

        assertThat(CheckWeek.of(utc).windowOpen()).isFalse();
        assertThat(CheckWeek.of(utc.withZoneSameInstant(KST)).windowOpen()).isTrue();
    }
}
