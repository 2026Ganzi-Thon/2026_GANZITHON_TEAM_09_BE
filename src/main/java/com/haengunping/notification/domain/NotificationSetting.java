package com.haengunping.notification.domain;

import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;

/** 알림 설정(notification_settings, NOTI-02·03). 행이 없으면 기본값(미션 알림 끔, 점검 알림 켬). */
@Entity
@Table(name = "notification_settings")
public class NotificationSetting {

    @Id
    @Column(name = "user_id")
    private long userId;

    @Column(name = "mission_reminder_time")
    private LocalTime missionReminderTime;

    @Column(name = "weekly_check_reminder")
    private boolean weeklyCheckReminder;

    protected NotificationSetting() {
    }

    /** 미션 알림 시각은 30분 단위(정각·30분)만. null은 끔. 어기면 400. */
    public static void validateReminderTime(LocalTime time) {
        if (time != null && (time.getMinute() % 30 != 0 || time.getSecond() != 0 || time.getNano() != 0)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "알림 시각은 30분 단위로 골라 주세요");
        }
    }

    public LocalTime missionReminderTime() {
        return missionReminderTime;
    }

    public boolean weeklyCheckReminder() {
        return weeklyCheckReminder;
    }
}
