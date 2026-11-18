package com.haengunping.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 테스트가 "지금"을 정하는 시계. 통합 테스트에서 {@code @TestConfiguration}의 {@code @Primary} 빈으로 넣어
 * 일요일 저녁 같은 요일·시각 조건을 실제 앱 전체로 확인한다. 시간대는 앱 시계와 같은 KST다.
 */
public class MutableClock extends Clock {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private volatile Instant now;

    public MutableClock(String kstDateTime) {
        set(kstDateTime);
    }

    /** 예: {@code "2026-11-22T18:00:00"}(KST). */
    public void set(String kstDateTime) {
        this.now = LocalDateTime.parse(kstDateTime).atZone(KST).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return KST;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
