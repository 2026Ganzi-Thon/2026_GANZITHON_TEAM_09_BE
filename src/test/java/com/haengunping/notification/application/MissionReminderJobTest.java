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
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/** 미션 알림(NOTI-02)을 실제 PostgreSQL로 확인한다. 발송만 가짜로 바꿔 누구에게 몇 번 보냈는지 본다. */
class MissionReminderJobTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final LocalTime SLOT = LocalTime.of(12, 30);
    private static final short TOPIC = 951;

    @Autowired
    MissionReminderJob job;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    PushSender sender;
    @Autowired
    NotificationSettingRepository settings;
    @Autowired
    TransactionTemplate tx;

    private long notDone;
    private long done;
    private long otherTime;
    private long off;

    @BeforeEach
    void setUp() {
        notDone = createUser();
        done = createUser();
        otherTime = createUser();
        off = createUser();
        setting(notDone, SLOT);
        setting(done, SLOT);
        setting(otherTime, LocalTime.of(13, 0));
        setting(off, null);
        jdbc.update("""
                INSERT INTO mission_topics (id, category, outdoor_text, indoor_text, size_type, priority_moods)
                VALUES (?, 'ACTIVITY', '밖', '안', 'TIME', '{OKAY}'::varchar[])
                """, TOPIC);
        jdbc.update("INSERT INTO daily_missions (user_id, date, topic_id, completed_at) VALUES (?, ?, ?, now())", done, TODAY, TOPIC);
    }

    @AfterEach
    void cleanUp() {
        List<Long> users = List.of(notDone, done, otherTime, off);
        for (long u : users) {
            jdbc.update("DELETE FROM daily_missions WHERE user_id = ?", u);
            jdbc.update("DELETE FROM notification_settings WHERE user_id = ?", u);
            deleteUser(u);
        }
        jdbc.update("DELETE FROM mission_topics WHERE id = ?", TOPIC);
    }

    @Test
    void NOTI02_알림_시각이고_오늘_미완료인_사람에게만_보낸다() {
        assertThat(job.remindAt(TODAY, SLOT)).isEqualTo(1);

        verify(sender).send(notDone, PushMessage.MISSION_REMINDER);
        verify(sender, never()).send(eq(done), eq(PushMessage.MISSION_REMINDER));
        verify(sender, never()).send(eq(otherTime), eq(PushMessage.MISSION_REMINDER));
        verify(sender, never()).send(eq(off), eq(PushMessage.MISSION_REMINDER));
    }

    @Test
    void NOTI02_같은_시각에_두_번_돌아도_하루_1번() {
        job.remindAt(TODAY, SLOT);

        assertThat(job.remindAt(TODAY, SLOT)).isZero();
        verify(sender, times(1)).send(anyLong(), eq(PushMessage.MISSION_REMINDER));
    }

    @Test
    void NOTI02_다음_날에는_다시_보낸다() {
        job.remindAt(TODAY, SLOT);

        assertThat(job.remindAt(TODAY.plusDays(1), SLOT)).isEqualTo(2); // 완료 기록은 어제 것이라 done도 대상
    }

    // 앱과 같은 경로(JPA)로 넣는다. hibernate.jdbc.time_zone=UTC라 JDBC로 직접 넣은 TIME 값과는 어긋난다.
    private void setting(long userId, LocalTime time) {
        tx.executeWithoutResult(s -> settings.upsert(userId, time, true));
    }
}
