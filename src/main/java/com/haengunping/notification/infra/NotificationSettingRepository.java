package com.haengunping.notification.infra;

import com.haengunping.notification.domain.NotificationSetting;
import java.time.LocalTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO notification_settings (user_id, mission_reminder_time, weekly_check_reminder)
            VALUES (:userId, :missionReminderTime, :weeklyCheckReminder)
            ON CONFLICT (user_id) DO UPDATE
            SET mission_reminder_time = EXCLUDED.mission_reminder_time, weekly_check_reminder = EXCLUDED.weekly_check_reminder
            """)
    void upsert(long userId, LocalTime missionReminderTime, boolean weeklyCheckReminder);
}
