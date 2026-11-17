package com.haengunping.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haengunping.common.error.ApiException;
import com.haengunping.notification.application.NotificationService.Settings;
import com.haengunping.notification.domain.PushSubscription;
import com.haengunping.notification.infra.PushSubscriptionRepository;
import com.haengunping.support.IntegrationTest;
import java.time.LocalTime;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationServiceTest extends IntegrationTest {

    @Autowired
    NotificationService service;
    @Autowired
    PushSubscriptionRepository subscriptions;
    @Autowired
    JdbcTemplate jdbc;

    private final long me = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    private final long other = me - 1;
    private final String endpoint = "https://fcm.googleapis.com/fcm/send/" + me;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id IN (?, ?)", me, other);
        jdbc.update("DELETE FROM notification_settings WHERE user_id IN (?, ?)", me, other);
    }

    @Test
    void NOTI01_같은_endpoint로_다시_구독하면_덮어쓴다() {
        service.subscribe(me, endpoint, "p1", "a1");
        service.subscribe(me, endpoint, "p2", "a2");

        assertThat(subscriptions.findByUserId(me)).singleElement().satisfies(s -> {
            assertThat(s.endpoint()).isEqualTo(endpoint);
            assertThat(s.p256dh()).isEqualTo("p2");
            assertThat(s.auth()).isEqualTo("a2");
        });
    }

    @Test
    void NOTI01_같은_기기에서_새_계정이_구독하면_새_사용자에게_옮겨_간다() {
        service.subscribe(other, endpoint, "p", "a");
        service.subscribe(me, endpoint, "p", "a");

        assertThat(subscriptions.findByUserId(other)).isEmpty();
        assertThat(subscriptions.findByUserId(me)).extracting(PushSubscription::userId).containsExactly(me);
    }

    @Test
    void NOTI01_허용_안_된_주소는_저장하지_않는다() {
        assertThatThrownBy(() -> service.subscribe(me, "https://localhost/x", "p", "a")).isInstanceOf(ApiException.class);
        assertThat(subscriptions.findByUserId(me)).isEmpty();
    }

    @Test
    void NOTI01_남의_구독은_해지할_수_없다() {
        service.subscribe(other, endpoint, "p", "a");

        service.unsubscribe(me, endpoint);
        assertThat(subscriptions.findByUserId(other)).hasSize(1);

        service.unsubscribe(other, endpoint);
        assertThat(subscriptions.findByUserId(other)).isEmpty();
    }

    @Test
    void NOTI02_설정이_없으면_미션_알림_끔_점검_알림_켬() {
        assertThat(service.settings(me)).isEqualTo(new Settings(null, true));
    }

    @Test
    void NOTI02_설정을_저장하고_다시_바꿀_수_있다() {
        service.updateSettings(me, LocalTime.of(12, 30), false);
        assertThat(service.settings(me)).isEqualTo(new Settings(LocalTime.of(12, 30), false));

        service.updateSettings(me, null, true);
        assertThat(service.settings(me)).isEqualTo(new Settings(null, true));
    }

    @Test
    void NOTI02_30분_단위가_아니면_저장하지_않는다() {
        assertThatThrownBy(() -> service.updateSettings(me, LocalTime.of(12, 15), true)).isInstanceOf(ApiException.class);
        assertThat(service.settings(me)).isEqualTo(new Settings(null, true));
    }
}
