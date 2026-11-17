package com.haengunping.notification.domain;

import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;

/** 웹 푸시 구독(push_subscriptions, NOTI-01). 저장은 PushSubscriptionRepository.upsert로만 한다. */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    /**
     * 서버가 이 주소로 직접 요청을 보내므로(발송), 아무 URL이나 받으면 내부망을 찌르는 통로가 된다(SSRF).
     * 브라우저 푸시 서비스의 https 주소만 받는다.
     * shortcut: 아래 목록 밖의 브라우저 푸시 서비스는 거부된다, 실제 기기에서 거부가 보이면 그 호스트를 더한다.
     */
    static final List<String> ALLOWED_HOST_SUFFIXES = List.of(
            "fcm.googleapis.com",               // Chrome, 안드로이드
            "updates.push.services.mozilla.com", // Firefox
            "push.apple.com",                    // Safari, iOS 홈 화면 PWA
            "notify.windows.com");               // Edge

    static final int MAX_ENDPOINT_LENGTH = 2048;
    static final int MAX_P256DH_LENGTH = 100;
    static final int MAX_AUTH_LENGTH = 50;

    @Id
    private Long id;

    @Column(name = "user_id")
    private long userId;

    private String endpoint;

    private String p256dh;

    private String auth;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PushSubscription() {
    }

    /** 구독 값 검사. 어기면 400. */
    public static void validate(String endpoint, String p256dh, String auth) {
        if (!isAllowedEndpoint(endpoint) || !fits(p256dh, MAX_P256DH_LENGTH) || !fits(auth, MAX_AUTH_LENGTH)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    static boolean isAllowedEndpoint(String endpoint) {
        if (!fits(endpoint, MAX_ENDPOINT_LENGTH)) {
            return false;
        }
        try {
            URI uri = new URI(endpoint);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1 && host != null
                    && ALLOWED_HOST_SUFFIXES.stream().anyMatch(s -> host.equals(s) || host.endsWith("." + s));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean fits(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }

    public long userId() {
        return userId;
    }

    public String endpoint() {
        return endpoint;
    }

    public String p256dh() {
        return p256dh;
    }

    public String auth() {
        return auth;
    }
}
