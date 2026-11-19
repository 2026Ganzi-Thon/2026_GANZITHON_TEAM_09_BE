package com.haengunping.notification.infra;

import com.haengunping.notification.domain.NotificationSetting;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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

    @Modifying
    @Query("DELETE FROM NotificationSetting s WHERE s.userId = :userId")
    int deleteByUserId(long userId);

    /** 알림 시각이 time이고 오늘 아직 미션 알림을 받지 않은 사용자. */
    @Query(nativeQuery = true, value = """
            SELECT user_id FROM notification_settings
            WHERE mission_reminder_time = :time AND (mission_reminded_on IS NULL OR mission_reminded_on < :today)
            """)
    List<Long> findDueForMissionReminder(LocalTime time, LocalDate today);

    /**
     * 점검 알림을 받을 사용자(NOTI-03): 푸시 구독이 있고 점검 알림을 끄지 않은 사람.
     * 설정 행이 없으면 기본값(켜짐)이다. 온보딩은 행을 만들지 않으므로 행이 있는 사람만 고르면 대부분 빠진다.
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT p.user_id FROM push_subscriptions p
            LEFT JOIN notification_settings s ON s.user_id = p.user_id
            WHERE s.user_id IS NULL OR s.weekly_check_reminder
            """)
    List<Long> findWeeklyCheckReminderTargets();

    /** 오늘 보냄으로 표시한다. 1이면 이 실행이 보낼 차례, 0이면 이미 다른 실행이 보냈다. */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE notification_settings SET mission_reminded_on = :today
            WHERE user_id = :userId AND (mission_reminded_on IS NULL OR mission_reminded_on < :today)
            """)
    int claimMissionReminder(long userId, LocalDate today);
}
