package com.haengunping.notification.domain;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 알림 문구. 잠금 화면에 보이므로 기분·점검 내용과 결과를 넣지 않는다(NOTI-04).
 * 문구는 고정값만 쓴다(사용자 입력이 들어가지 않으므로 JSON 이스케이프가 필요 없다).
 */
public enum PushMessage {
    // TTL: 늦게 도착하면 의미 없는 알림이라 짧게. 미션은 다음 알림 시각 전에, 점검은 일요일 18:00부터 그날 안에.
    MISSION_REMINDER("행운핑", "오늘의 작은 미션, 아직 시간 있어요", "/", Duration.ofHours(1)),
    WEEKLY_CHECK_REMINDER("행운핑", "이번 주를 잠깐 돌아볼 시간이에요", "/", Duration.ofHours(6));

    private final String title;
    private final String body;
    private final String url;
    private final Duration ttl;

    PushMessage(String title, String body, String url, Duration ttl) {
        this.title = title;
        this.body = body;
        this.url = url;
        this.ttl = ttl;
    }

    /** 푸시 서비스가 기기에 못 전하면 이 시간 뒤에 버린다. */
    public Duration ttl() {
        return ttl;
    }

    /** 서비스 워커가 받는 내용: {@code {"title","body","url"}}. */
    public byte[] payload() {
        return ("{\"title\":\"" + title + "\",\"body\":\"" + body + "\",\"url\":\"" + url + "\"}").getBytes(StandardCharsets.UTF_8);
    }
}
