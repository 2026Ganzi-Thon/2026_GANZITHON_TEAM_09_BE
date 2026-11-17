package com.haengunping.notification.application;

import com.haengunping.notification.domain.NotificationSetting;
import com.haengunping.notification.domain.PushSubscription;
import com.haengunping.notification.infra.NotificationSettingRepository;
import com.haengunping.notification.infra.PushSubscriptionRepository;
import java.time.Clock;
import java.time.LocalTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 웹 푸시 구독과 알림 설정(NOTI-01~03). */
@Service
@Transactional
public class NotificationService {

    private static final Settings DEFAULTS = new Settings(null, true);

    private final PushSubscriptionRepository subscriptions;
    private final NotificationSettingRepository settings;
    private final Clock clock;

    public NotificationService(PushSubscriptionRepository subscriptions, NotificationSettingRepository settings, Clock clock) {
        this.subscriptions = subscriptions;
        this.settings = settings;
        this.clock = clock;
    }

    public void subscribe(long userId, String endpoint, String p256dh, String auth) {
        PushSubscription.validate(endpoint, p256dh, auth);
        subscriptions.upsert(userId, endpoint, p256dh, auth, clock.instant());
    }

    /** 내 구독만 지운다. 없는 endpoint여도 조용히 끝낸다(204). */
    public void unsubscribe(long userId, String endpoint) {
        subscriptions.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    @Transactional(readOnly = true)
    public Settings settings(long userId) {
        return settings.findById(userId)
                .map(s -> new Settings(s.missionReminderTime(), s.weeklyCheckReminder()))
                .orElse(DEFAULTS);
    }

    public Settings updateSettings(long userId, LocalTime missionReminderTime, boolean weeklyCheckReminder) {
        NotificationSetting.validateReminderTime(missionReminderTime);
        settings.upsert(userId, missionReminderTime, weeklyCheckReminder);
        return new Settings(missionReminderTime, weeklyCheckReminder);
    }

    public record Settings(LocalTime missionReminderTime, boolean weeklyCheckReminder) {
    }
}
