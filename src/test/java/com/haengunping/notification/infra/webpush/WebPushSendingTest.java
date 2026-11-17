package com.haengunping.notification.infra.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haengunping.notification.application.PushSender;
import com.haengunping.notification.domain.PushMessage;
import com.haengunping.notification.infra.PushSubscriptionRepository;
import com.haengunping.support.IntegrationTest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 로컬 HTTP 서버를 푸시 서비스로 세워 실제로 보내 본다. 410이면 구독이 지워지는지(NOTI-05) 확인한다. */
class WebPushSendingTest extends IntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-16T03:00:00Z");
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    @Autowired
    PushSubscriptionRepository subscriptions;
    @Autowired
    JdbcTemplate jdbc;

    private long me;
    private final Map<String, Integer> statusByPath = new ConcurrentHashMap<>();
    private final List<Map<String, String>> received = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private KeyPair vapidKeys;
    private PushSender sender;

    @BeforeEach
    void setUp() throws Exception {
        me = createUser();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            received.add(Map.of(
                    "authorization", exchange.getRequestHeaders().getFirst("Authorization"),
                    "encoding", exchange.getRequestHeaders().getFirst("Content-Encoding"),
                    "ttl", exchange.getRequestHeaders().getFirst("TTL")));
            exchange.sendResponseHeaders(statusByPath.getOrDefault(exchange.getRequestURI().getPath(), 201), -1);
            exchange.close();
        });
        server.start();

        vapidKeys = WebPushCrypto.newKeyPair();
        Vapid vapid = new Vapid(
                B64.encodeToString(WebPushCrypto.encode((ECPublicKey) vapidKeys.getPublic())),
                B64.encodeToString(rawPrivate(vapidKeys)),
                "mailto:team@example.com");
        sender = new PushSender(subscriptions, new WebPushClient(vapid, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ?", me);
        deleteUser(me);
    }

    @Test
    void NOTI05_만료된_구독_410은_지우고_살아_있는_구독은_남긴다() {
        statusByPath.put("/gone", 410);
        subscribe("/alive");
        subscribe("/gone");

        int delivered = sender.send(me, PushMessage.MISSION_REMINDER);

        assertThat(delivered).isEqualTo(1);
        assertThat(subscriptions.findByUserId(me)).extracting(s -> URI.create(s.endpoint()).getPath()).containsExactly("/alive");
    }

    @Test
    void NOTI05_푸시_서비스_오류_500은_구독을_남기고_넘어간다() {
        statusByPath.put("/broken", 500);
        subscribe("/broken");

        assertThat(sender.send(me, PushMessage.MISSION_REMINDER)).isZero();
        assertThat(subscriptions.findByUserId(me)).hasSize(1);
    }

    @Test
    void NOTI01_암호화_헤더와_VAPID_서명을_붙여_보낸다() throws Exception {
        subscribe("/alive");

        sender.send(me, PushMessage.WEEKLY_CHECK_REMINDER);

        Map<String, String> request = received.getFirst();
        assertThat(request.get("encoding")).isEqualTo("aes128gcm");
        assertThat(request.get("ttl")).isEqualTo("21600"); // 점검 알림 6시간

        String auth = request.get("authorization");
        assertThat(auth).startsWith("vapid t=").contains(", k=" + B64.encodeToString(WebPushCrypto.encode((ECPublicKey) vapidKeys.getPublic())));
        String[] jwt = auth.substring("vapid t=".length(), auth.indexOf(", k=")).split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8);
        assertThat(claims)
                .contains("\"aud\":\"http://127.0.0.1:" + server.getAddress().getPort() + "\"")
                .contains("\"exp\":" + NOW.plus(Vapid.TOKEN_LIFETIME).getEpochSecond())
                .contains("\"sub\":\"mailto:team@example.com\"");

        Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
        es256.initVerify(vapidKeys.getPublic());
        es256.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(es256.verify(Base64.getUrlDecoder().decode(jwt[2]))).isTrue();
    }

    @Test
    void 키가_없으면_아무것도_보내지_않는다() {
        subscribe("/alive");

        assertThat(new PushSender(subscriptions, WebPushClient.disabled()).send(me, PushMessage.MISSION_REMINDER)).isZero();
        assertThat(received).isEmpty();
    }

    @Test
    void NOTI04_알림_문구는_고정값이고_기분_점검_내용이_없다() {
        for (PushMessage message : PushMessage.values()) {
            String payload = new String(message.payload(), StandardCharsets.UTF_8);
            assertThat(payload).startsWith("{\"title\":").doesNotContain("우울", "불안", "기분", "점검 결과");
        }
    }

    @Test
    void VAPID_주체는_mailto나_https만_받는다() {
        assertThatThrownBy(() -> new Vapid("x", "y", "team@example.com")).isInstanceOf(IllegalArgumentException.class);
    }

    private void subscribe(String path) {
        jdbc.update("INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, created_at) VALUES (?, ?, ?, ?, now())",
                me, "http://127.0.0.1:" + server.getAddress().getPort() + path,
                WebPushCryptoTest.UA_PUBLIC, WebPushCryptoTest.AUTH_SECRET);
    }

    private static byte[] rawPrivate(KeyPair keys) {
        byte[] s = ((java.security.interfaces.ECPrivateKey) keys.getPrivate()).getS().toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(s.length, 32);
        System.arraycopy(s, s.length - copy, out, 32 - copy, copy);
        return out;
    }
}
