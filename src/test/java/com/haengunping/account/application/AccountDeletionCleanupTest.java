package com.haengunping.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.haengunping.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 기록·계정 삭제가 다른 모듈의 행까지 실제로 지우는지 실제 PostgreSQL로 확인한다(erd.md 6절).
 * account는 이벤트만 발행하므로, 받는 쪽이 없으면 204를 주고도 아무것도 안 지워진다(2026-11-18에 그랬다).
 */
class AccountDeletionCleanupTest extends IntegrationTest {

    @Autowired
    AccountService accounts;
    @Autowired
    JdbcTemplate jdbc;

    private long me;
    private long other;

    @BeforeEach
    void setUp() {
        me = createUser();
        other = createUser();
        seed(me);
        seed(other);
    }

    @AfterEach
    void tearDown() {
        for (long id : new long[] {me, other}) {
            for (String table : new String[] {"moods", "help_card_dismissals", "push_subscriptions", "notification_settings", "daily_missions", "checks"}) {
                jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", id);
            }
            deleteUser(id);
        }
    }

    @Test
    void ACC07_기분_점검_기록_삭제는_기분과_점검만_지우고_나머지는_남긴다() {
        accounts.eraseHealthRecords(me);

        assertThat(count("moods", me)).isZero();
        assertThat(count("checks", me)).isZero();
        assertThat(count("checks", other)).isOne();
        assertThat(count("help_card_dismissals", me)).isOne();
        assertThat(count("push_subscriptions", me)).isOne();
        assertThat(count("notification_settings", me)).isOne();
        assertThat(count("daily_missions", me)).isOne();
        assertThat(count("moods", other)).isOne();
    }

    @Test
    void ACC08_계정_삭제는_기분_점검_도움카드_푸시구독_알림설정_미션을_지운다() {
        accounts.delete(me);

        assertThat(count("moods", me)).isZero();
        assertThat(count("checks", me)).isZero();
        assertThat(count("checks", other)).isOne();
        assertThat(count("help_card_dismissals", me)).isZero();
        assertThat(count("push_subscriptions", me)).isZero();
        assertThat(count("notification_settings", me)).isZero();
        assertThat(count("daily_missions", me)).isZero();
        // users 행은 남기고 지운 시각만 남는다(erd.md 6절)
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, me)).isTrue();
        // 다른 사람 기록은 그대로
        assertThat(count("moods", other)).isOne();
        assertThat(count("push_subscriptions", other)).isOne();
        assertThat(count("daily_missions", other)).isOne();
    }

    private void seed(long userId) {
        jdbc.update("INSERT INTO moods (user_id, date, mood, updated_at) VALUES (?, DATE '2026-11-16', 'LONELY', now())", userId);
        jdbc.update("INSERT INTO checks (user_id, week_start, status, result_codes, created_at) "
                + "VALUES (?, DATE '2026-11-16', 'DONE', ARRAY['DEPRESSION']::varchar[], now())", userId);
        jdbc.update("INSERT INTO help_card_dismissals (user_id, dismissed_at) VALUES (?, now())", userId);
        jdbc.update("INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, created_at) VALUES (?, ?, 'p', 'a', now())",
                userId, "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID());
        jdbc.update("INSERT INTO notification_settings (user_id, weekly_check_reminder) VALUES (?, true)", userId);
        jdbc.update("INSERT INTO daily_missions (user_id, date, topic_id, completed_at) VALUES (?, DATE '2026-11-16', 1, now())", userId);
    }

    private int count(String table, long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }
}
