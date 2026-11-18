package com.haengunping.notification.infra.webpush;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * 푸시 서비스로 암호화한 메시지 1건을 보낸다. 타임아웃 3초(NFR-05).
 * VAPID 키가 없으면(로컬·테스트) 아무것도 보내지 않는다.
 */
public class WebPushClient {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Vapid vapid;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER) // 허용한 푸시 서비스 밖으로 끌려가지 않게
            .build();

    WebPushClient(Vapid vapid, Clock clock) {
        this.vapid = vapid;
        this.clock = clock;
    }

    public static WebPushClient disabled() {
        return new WebPushClient(null, null);
    }

    public boolean isEnabled() {
        return vapid != null;
    }

    /** @return 푸시 서비스의 HTTP 상태 코드. 404·410이면 구독이 만료된 것이다(NOTI-05). */
    public int send(String endpoint, String p256dh, String auth, byte[] payload, Duration ttl) throws IOException, InterruptedException {
        if (!isEnabled()) {
            throw new IllegalStateException("VAPID 키가 없다");
        }
        URI uri = URI.create(endpoint);
        byte[] body;
        String authorization;
        try {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            Base64.Decoder b64 = Base64.getUrlDecoder();
            body = WebPushCrypto.encrypt(payload, b64.decode(p256dh), b64.decode(auth), WebPushCrypto.newKeyPair(), salt);
            authorization = vapid.authorization(uri, clock.instant());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("웹 푸시 암호화 실패(구독 키 형식 오류)", e);
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Authorization", authorization)
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", String.valueOf(ttl.toSeconds()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
