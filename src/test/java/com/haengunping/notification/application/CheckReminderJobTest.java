package com.haengunping.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
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
    private long noSettingRow;
    private long noSubscription;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM job_runs WHERE job_name = 'CHECK_REMINDER'");
        due = user(true);
        submitted = user(true);
        dismissed = user(true);
        lastWeekOnly = user(true);
        off = user(false);
        noConsent = user(true);
        noSettingRow = user(null); // 온보딩만 하고 설정 화면은 안 연 사람: 기본 켜짐
        noSubscription = createUser();
        consent(noSubscription);
        tx.executeWithoutResult(s -> settings.upsert(noSubscription, null, true));
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
        for (long u : List.of(due, submitted, dismissed, lastWeekOnly, off, noConsent, noSettingRow, noSubscription)) {
            jdbc.update("DELETE FROM checks WHERE user_id = ?", u);
            jdbc.update("DELETE FROM notification_settings WHERE user_id = ?", u);
            jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ?", u);
            deleteUser(u);
        }
    }

    @Test
    void NOTI03_알림을_켰고_이번_주_점검을_하지도_닫지도_않은_사람에게만_보낸다() {
        job.remindOn(SUNDAY); // 다른 테스트가 남긴 구독자도 대상이라 총 수 대신 이 테스트의 사용자만 본다

        verify(sender).send(due, PushMessage.WEEKLY_CHECK_REMINDER);
        verify(sender).send(noSettingRow, PushMessage.WEEKLY_CHECK_REMINDER);
        verify(sender).send(lastWeekOnly, PushMessage.WEEKLY_CHECK_REMINDER); // 지난주 기록은 이번 주와 무관
        verify(sender, never()).send(eq(submitted), eq(PushMessage.WEEKLY_CHECK_REMINDER));
        verify(sender, never()).send(eq(dismissed), eq(PushMessage.WEEKLY_CHECK_REMINDER));
        verify(sender, never()).send(eq(off), eq(PushMessage.WEEKLY_CHECK_REMINDER));
        verify(sender, never()).send(eq(noConsent), eq(PushMessage.WEEKLY_CHECK_REMINDER)); // 동의 없으면 점검도 못 한다(ACC-05)
        verify(sender, never()).send(eq(noSubscription), eq(PushMessage.WEEKLY_CHECK_REMINDER)); // 받을 기기가 없다
    }

    @Test
    void NOTI03_같은_일요일에_두_번_돌아도_1번() {
        job.remindOn(SUNDAY);

        assertThat(job.remindOn(SUNDAY)).isZero();
        verify(sender, times(1)).send(due, PushMessage.WEEKLY_CHECK_REMINDER);
    }

    /** 동의하고 푸시를 구독한 사용자. weeklyCheckReminder가 null이면 설정 행을 만들지 않는다. */
    private long user(Boolean weeklyCheckReminder) {
        long id = createUser();
        consent(id);
        jdbc.update("INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, created_at) VALUES (?, ?, 'p', 'a', now())",
                id, "https://push.example/check-reminder/" + id);
        if (weeklyCheckReminder != null) {
            tx.executeWithoutResult(s -> settings.upsert(id, null, weeklyCheckReminder));
        }
        return id;
    }

    private void consent(long id) {
        jdbc.update("UPDATE users SET sensitive_consent_at = now() WHERE id = ?", id);
    }
}
