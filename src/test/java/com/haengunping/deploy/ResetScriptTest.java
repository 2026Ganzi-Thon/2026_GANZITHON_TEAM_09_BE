package com.haengunping.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.haengunping.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

/**
 * 실서버 비우기(#57, deploy/reset.sql)를 실제 PostgreSQL 16에서 확인한다.
 * 운영과 같은 방식으로 {@code psql -v ON_ERROR_STOP=1 -1}로 실행하므로 deploy.sh reset이 하는 일 그대로다.
 * 이 테스트는 공유 테스트 DB를 비운다. 통합 테스트는 순서대로 돌고 각자 자기 데이터를 만들므로 영향이 없다.
 */
class ResetScriptTest extends IntegrationTest {

    /** deploy/reset.sql의 남길 테이블과 같아야 한다(스크립트를 바꾸면 여기도 바꾼다). */
    private static final List<String> KEEP = List.of("flyway_schema_history", "help_items", "mission_topics", "service_sync_log");

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void emptyFirst() {
        reset(); // 다른 테스트가 남긴 행 없이 시작한다
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("DROP TABLE IF EXISTS zz_reset_new_user_data");
        jdbc.execute("ALTER TABLE help_items DROP COLUMN IF EXISTS zz_user_ref");
        reset();
    }

    @Test
    void 사용자_데이터는_모두_지우고_시드는_그대로_남긴다() {
        seedEverything();
        int helpItems = count("help_items");
        int topics = count("mission_topics");
        int history = count("flyway_schema_history");
        assertThat(helpItems).isPositive();
        assertThat(topics).isPositive();

        ExecResult result = reset();

        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        for (String table : seededUserTables()) {
            assertThat(count(table)).as(table).isZero();
        }
        assertThat(count("help_items")).isEqualTo(helpItems);
        assertThat(count("mission_topics")).isEqualTo(topics);
        assertThat(count("flyway_schema_history")).isEqualTo(history);
    }

    @Test
    void 남길_것_말고는_모든_테이블이_비어_있다_테이블을_빼먹지_않았다는_확인() {
        seedEverything();

        reset();

        List<String> notEmpty = new ArrayList<>();
        for (String table : publicTables()) {
            if (!KEEP.contains(table) && count(table) > 0) {
                notEmpty.add(table);
            }
        }
        assertThat(notEmpty).isEmpty();
    }

    @Test
    void 나중에_생긴_테이블도_목록에_없으면_기본으로_지운다() {
        jdbc.execute("CREATE TABLE zz_reset_new_user_data (id int)");
        jdbc.update("INSERT INTO zz_reset_new_user_data VALUES (1)");

        reset();

        assertThat(count("zz_reset_new_user_data")).isZero();
    }

    @Test
    void 식별자_번호가_처음부터_다시_시작한다() {
        long before = createUser();
        deleteUser(before);
        assertThat(before).isPositive();

        reset();

        long first = createUser();
        try {
            assertThat(first).isEqualTo(1L);
        } finally {
            deleteUser(first);
        }
    }

    @Test
    void 남길_테이블이_지울_테이블을_참조하면_조용히_같이_지우지_않고_멈춘다() {
        long user = createUser();
        // 누군가 시드 테이블에서 users를 참조하게 만든 상황. CASCADE였다면 시드도 함께 지워졌을 것이다.
        jdbc.execute("ALTER TABLE help_items ADD COLUMN zz_user_ref bigint REFERENCES users(id)");
        int helpItems = count("help_items");

        ExecResult result = reset();

        assertThat(result.getExitCode()).isNotZero();
        assertThat(count("users")).as("멈췄으면 아무것도 지워지지 않는다").isEqualTo(1);
        assertThat(count("help_items")).isEqualTo(helpItems);
        assertThat(user).isPositive();
    }

    // ---- helpers

    /** deploy.sh reset과 같은 명령: 스크립트를 표준 입력으로 넣는 psql -v ON_ERROR_STOP=1 -1 -f - */
    private ExecResult reset() {
        try {
            String inContainer = "/tmp/reset-" + UUID.randomUUID() + ".sql";
            POSTGRES.copyFileToContainer(MountableFile.forHostPath("deploy/reset.sql"), inContainer);
            return POSTGRES.execInContainer("sh", "-c",
                    "psql -U " + POSTGRES.getUsername() + " -d " + POSTGRES.getDatabaseName()
                            + " -v ON_ERROR_STOP=1 -1 -f - < " + inContainer);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private List<String> publicTables() {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
    }

    /** seedEverything이 채우는 사용자 데이터 테이블. */
    private List<String> seededUserTables() {
        return List.of("users", "groups", "group_members", "invites", "online_queue", "blocks", "reports",
                "characters", "daily_group_progress", "moods", "daily_missions", "help_card_dismissals",
                "push_subscriptions", "notification_settings", "job_runs");
    }

    /** 사용자 데이터가 들어가는 테이블마다 한 행 이상, 외래 키로 이어진 것까지 채운다. */
    private void seedEverything() {
        long u1 = createUser();
        long u2 = createUser();
        long group = createGroup();
        long m1 = member(group, u1, "하나");
        long m2 = member(group, u2, "둘");
        jdbc.update("INSERT INTO invites (group_id, code, created_by_member_id, expires_at) VALUES (?, 'ABCD2345', ?, now() + interval '1 day')", group, m1);
        jdbc.update("INSERT INTO online_queue (user_id, queued_at) VALUES (?, now())", u1);
        jdbc.update("INSERT INTO blocks (blocker_member_id, blocked_member_id, created_at) VALUES (?, ?, now())", m1, m2);
        jdbc.update("INSERT INTO reports (group_id, reporter_member_id, target_member_id, reason, created_at) VALUES (?, ?, ?, 'SPAM', now())", group, m1, m2);
        jdbc.update("INSERT INTO characters (group_id, kind, started_on) VALUES (?, 'SPROUT', DATE '2026-11-16')", group);
        jdbc.update("INSERT INTO daily_group_progress (group_id, date, member_count, completed_count, finalized_at) VALUES (?, DATE '2026-11-16', 2, 1, now())", group);
        jdbc.update("INSERT INTO moods (user_id, date, mood, updated_at) VALUES (?, DATE '2026-11-16', 'LONELY', now())", u1);
        jdbc.update("INSERT INTO daily_missions (user_id, date, topic_id, completed_at) VALUES (?, DATE '2026-11-16', 1, now())", u1);
        jdbc.update("INSERT INTO help_card_dismissals (user_id, dismissed_at) VALUES (?, now())", u1);
        jdbc.update("INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, created_at) VALUES (?, ?, 'p', 'a', now())",
                u1, "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID());
        jdbc.update("INSERT INTO notification_settings (user_id, weekly_check_reminder) VALUES (?, true)", u1);
        jdbc.update("INSERT INTO job_runs (job_name, run_key, status, started_at) VALUES ('FINALIZE_PROGRESS', DATE '2026-11-16', 'DONE', now())");
    }

    private long member(long group, long user, String name) {
        return jdbc.queryForObject(
                "INSERT INTO group_members (group_id, user_id, display_name, joined_at) VALUES (?, ?, ?, now()) RETURNING id",
                Long.class, group, user, name);
    }
}
