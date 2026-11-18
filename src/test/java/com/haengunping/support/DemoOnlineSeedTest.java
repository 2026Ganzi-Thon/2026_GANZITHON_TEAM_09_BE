package com.haengunping.support;

import static org.assertj.core.api.Assertions.assertThat;

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
 * DEMO-04 (#58). PostgreSQL 16에서 운영과 같은 psql 실행 방식으로 시드의 안전성을 검증한다.
 * 기존 IntegrationTest.POSTGRES가 package-private이므로 동일 패키지에 둔다.
 * 각 테스트는 전용 PostgreSQL 테스트 DB에서 데이터를 초기화하며, 운영 DB는 접속하지 않는다.
 */
class DemoOnlineSeedTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void before() {
        clearDemoTables();
    }

    @AfterEach
    void after() {
        clearDemoTables();
    }

    @Test
    void 시드_생성후_그룹당_네명과_진행중_캐릭터_한개를_검증한다() {
        assertSucceeded(runScript("deploy/seed-demo-online.sql"));
        assertSucceeded(runScript("deploy/verify-demo-online.sql"));

        assertThat(count("users")).isEqualTo(12);
        assertThat(count("groups")).isEqualTo(3);
        assertThat(count("group_members")).isEqualTo(12);
        assertThat(count("characters")).isEqualTo(3);
        assertThat(count("online_queue")).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM groups g
                WHERE g.type = 'ONLINE' AND g.is_demo AND g.archived_at IS NULL
                  AND (SELECT count(*) FROM group_members gm
                       WHERE gm.group_id = g.id AND gm.left_at IS NULL) = 4
                  AND (SELECT count(*) FROM characters c
                       WHERE c.group_id = g.id AND c.completed_on IS NULL
                         AND c.kind = 'SEED'
                         AND c.started_on = (g.created_at AT TIME ZONE 'Asia/Seoul')::date) = 1
                """, Integer.class)).isEqualTo(3);
    }

    @Test
    void 시드가_온전하면_재실행해도_아이디와_행수가_바뀌지_않는다() {
        assertSucceeded(runScript("deploy/seed-demo-online.sql"));
        List<Long> firstIds = jdbc.queryForList("SELECT id FROM characters ORDER BY id", Long.class);
        List<Long> firstGroupIds = jdbc.queryForList("SELECT id FROM groups ORDER BY id", Long.class);

        assertSucceeded(runScript("deploy/seed-demo-online.sql"));
        assertSucceeded(runScript("deploy/verify-demo-online.sql"));
        assertThat(jdbc.queryForList("SELECT id FROM characters ORDER BY id", Long.class)).isEqualTo(firstIds);
        assertThat(jdbc.queryForList("SELECT id FROM groups ORDER BY id", Long.class)).isEqualTo(firstGroupIds);
        assertThat(count("users")).isEqualTo(12);
        assertThat(count("characters")).isEqualTo(3);
    }

    @Test
    void 사용자가_추가된_뒤_시드를_다시_실행하면_기존데이터를_그대로_두고_실패한다() {
        assertSucceeded(runScript("deploy/seed-demo-online.sql"));
        jdbc.update("INSERT INTO users (last_seen_at, created_at) VALUES (now(), now())");

        ExecResult result = runScript("deploy/seed-demo-online.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isNotZero();
        assertThat(count("users")).isEqualTo(13);
        assertThat(count("groups")).isEqualTo(3);
        assertThat(count("characters")).isEqualTo(3);
    }

    @Test
    void 캐릭터가_빠진_시드는_정상으로_판단하지_않는다() {
        assertSucceeded(runScript("deploy/seed-demo-online.sql"));
        jdbc.update("DELETE FROM characters WHERE id = (SELECT min(id) FROM characters)");

        ExecResult result = runScript("deploy/seed-demo-online.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isNotZero();
        assertThat(count("groups")).isEqualTo(3);
        assertThat(count("characters")).isEqualTo(2);
    }

    @Test
    void 기존_사용자가_있는_DB에서는_시드를_만들지_않는다() {
        jdbc.update("INSERT INTO users (last_seen_at, created_at) VALUES (now(), now())");

        ExecResult result = runScript("deploy/seed-demo-online.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isNotZero();
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("groups")).isZero();
        assertThat(count("characters")).isZero();
        assertThat(result.getStderr())
                .contains("시연 시드는 빈 users/groups/group_members/online_queue/characters");
    }

    private void clearDemoTables() {
        // 전용 테스트 컨테이너에서만 실행한다. FK 연결 테이블까지 정리한다.
        jdbc.execute("TRUNCATE TABLE users, groups, group_members, online_queue, characters RESTART IDENTITY CASCADE");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static void assertSucceeded(ExecResult result) {
        if (result.getExitCode() != 0) {
            System.err.println("=== PostgreSQL 시드 실행 실패 ===");
            System.err.println("종료 코드: " + result.getExitCode());
            System.err.println("STDOUT:\n" + result.getStdout());
            System.err.println("STDERR:\n" + result.getStderr());
        }

        assertThat(result.getExitCode())
                .as("psql 실행 오류:\n%s", result.getStderr())
                .isZero();
    }

    private static ExecResult runScript(String path) {
        try {
            String inContainer = "/tmp/demo-online-" + UUID.randomUUID() + ".sql";
            POSTGRES.copyFileToContainer(MountableFile.forHostPath(path), inContainer);
            return POSTGRES.execInContainer("sh", "-c",
                    "psql -X -U " + POSTGRES.getUsername() + " -d " + POSTGRES.getDatabaseName()
                            + " -v ON_ERROR_STOP=1 -f " + inContainer);
        } catch (Exception e) {
            throw new IllegalStateException("PostgreSQL 시드 실행 실패: " + path, e);
        }
    }
}
