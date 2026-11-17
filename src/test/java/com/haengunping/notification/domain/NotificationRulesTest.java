package com.haengunping.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haengunping.common.error.ApiException;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/abc:def",
            "https://updates.push.services.mozilla.com/wpush/v2/abc",
            "https://web.push.apple.com/QGx1",
            "https://wns2-bn3p.notify.windows.com/w/?token=abc"})
    void NOTI01_브라우저_푸시_서비스_주소는_받는다(String endpoint) {
        assertThat(PushSubscription.isAllowedEndpoint(endpoint)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",          // https 아님
            "https://localhost/fcm",                            // 내부망
            "https://169.254.169.254/latest/meta-data",         // 클라우드 메타데이터
            "https://fcm.googleapis.com.evil.example/x",        // 접미사 속이기
            "https://evilfcm.googleapis.com.example/x",
            "https://notfcm.googleapis.com@evil.example/x",     // 사용자 정보로 호스트 숨기기
            "https://fcm.googleapis.com:8443/x",                // 다른 포트
            "not a url",
            ""})
    void NOTI01_그_밖의_주소는_거부한다_SSRF(String endpoint) {
        assertThat(PushSubscription.isAllowedEndpoint(endpoint)).isFalse();
    }

    @Test
    void NOTI01_너무_긴_주소나_빈_키는_400() {
        String ok = "https://fcm.googleapis.com/x";
        assertThat(PushSubscription.isAllowedEndpoint(ok + "a".repeat(PushSubscription.MAX_ENDPOINT_LENGTH))).isFalse();
        assertThatThrownBy(() -> PushSubscription.validate(ok, " ", "auth")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PushSubscription.validate(ok, "p", "a".repeat(51))).isInstanceOf(ApiException.class);
        assertThatCode(() -> PushSubscription.validate(ok, "p", "a")).doesNotThrowAnyException();
    }

    @Test
    void NOTI02_알림_시각은_정각과_30분만_받는다() {
        assertThatCode(() -> NotificationSetting.validateReminderTime(LocalTime.of(12, 30))).doesNotThrowAnyException();
        assertThatCode(() -> NotificationSetting.validateReminderTime(LocalTime.of(0, 0))).doesNotThrowAnyException();
        assertThatCode(() -> NotificationSetting.validateReminderTime(null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> NotificationSetting.validateReminderTime(LocalTime.of(12, 15))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> NotificationSetting.validateReminderTime(LocalTime.of(12, 30, 1))).isInstanceOf(ApiException.class);
    }
}
