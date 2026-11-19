package com.haengunping.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.haengunping.notification.domain.PushMessage;
import com.haengunping.notification.infra.NotificationSettingRepository;
import com.haengunping.support.IntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/** 점검 알림(NOTI-03)을 실제 PostgreSQL로 확인한다. 발송만 가짜로 바꿔 누구에게 몇 번 보냈는지 본다. */
class CheckReminderJobTest extends IntegrationTest {

    // 2026-10-05(월) ~ 10-11(일). 다른 테스트와 겹치지 않게 지난 주를 쓴다
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);

    @Autowired
    CheckReminderJob job;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    PushSender sender;
    @Autowired
    NotificationSettingRepository settings;
    @Autowired
    TransactionTemplate tx;

    private long due;
    private long submitted;
    private long dismissed;
    private long lastWeekOnly;
    private long off;
    private long noConsent;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM job_runs WHERE job_name = 'CHECK_REMINDER'");
        jdbc.update("DELETE FROM notification_settings"); // 다른 테스트가 남긴 '켠 사용자'가 섞이지 않게
        due = user(true);
        submitted = user(true);
        dismissed = user(true);
        lastWeekOnly = user(true);
        off = user(false);
        noConsent = user(true);
        jdbc.update("UPDATE users SET sensitive_consent_at = NULL WHERE id = ?", noConsent);
        jdbc.update("INSERT INTO checks (user_id, week_start, status, result_codes, created_at) VALUES (?, ?, 'DONE', '{STABLE}', now())",
                submitted, MONDAY);
        jdbc.update("INSERT INTO checks (user_id, week_start, status, created_at) VALUES (?, ?, 'DISMISSED', now())", dismissed, MONDAY);
        jdbc.update("INSERT INTO checks (user_id, week_start, status, created_at) VALUES (?, ?, 'DISMISSED', now())",
                lastWeekOnly, MONDAY.minusDays(7));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM job_runs WHERE job_name = 'CHECK_REMINDER'");
        for (long u : List.of(due, submitted, dismissed, lastWeekOnly, off, noConsent)) {
            jdbc.update("DELETE FROM checks WHERE user_id = ?", u);
            jdbc.update("DELETE FROM notification_settings WHERE user_id = ?", u);
            deleteUser(u);
        }
    }

    @Test
    void NOTI03_알림을_켰고_이번_주_점검을_하지도_닫지도_않은_사람에게만_보낸다() {
        assertThat(job.remindOn(SUNDAY)).isEqualTo(2);

        verify(sender).send(due, PushMessage.WEEKLY_CHECK_REMINDER);
        verify(sender).send(lastWeekOnly, PushMessage.WEEKLY_CHECK_REMINDER); // 지난주 기록은 이번 주와 무관
        verify(sender, never()).send(eq(submitted), eq(PushMessage.WEEKLY_CHECK_REMINDER));
        verify(sender, never()).send(eq(dismissed), eq(PushMessage.WEEKLY_CHECK_REMINDER));
        verify(sender, never()).send(eq(off), eq(PushMessage.WEEKLY_CHECK_REMINDER));
        verify(sender, never()).send(eq(noConsent), eq(PushMessage.WEEKLY_CHECK_REMINDER)); // 동의 없으면 점검도 못 한다(ACC-05)
    }

    @Test
    void NOTI03_같은_일요일에_두_번_돌아도_1번() {
        job.remindOn(SUNDAY);

        assertThat(job.remindOn(SUNDAY)).isZero();
        verify(sender, times(2)).send(anyLong(), eq(PushMessage.WEEKLY_CHECK_REMINDER));
    }

    private long user(boolean weeklyCheckReminder) {
        long id = createUser();
        jdbc.update("UPDATE users SET sensitive_consent_at = now() WHERE id = ?", id);
        tx.executeWithoutResult(s -> settings.upsert(id, null, weeklyCheckReminder));
        return id;
    }
}
