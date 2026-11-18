package com.haengunping.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 실제 PostgreSQL(운영과 같은 16)로 앱 전체를 띄우는 통합 테스트의 바탕. Docker가 필요하다.
 * 컨테이너는 테스트 실행 동안 하나만 띄워 모든 통합 테스트가 같이 쓴다.
 * 테스트끼리 데이터가 섞이지 않도록 각 테스트는 자기 데이터를 만들고 정리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private JdbcClient userJdbc;

    /** 외래 키를 만족하는 사용자 행을 만들고 id를 준다. 정리는 {@link #deleteUser}로, 그 사용자의 데이터를 지운 뒤에. */
    protected long createUser() {
        return userJdbc.sql("INSERT INTO users (last_seen_at, created_at) VALUES (now(), now()) RETURNING id")
                .query(Long.class)
                .single();
    }

    protected void deleteUser(long userId) {
        userJdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
    }

    /** 외래 키를 만족하는 그룹 행(구성원 없음)을 만들고 id를 준다. 정리는 {@link #deleteGroup}로, 그 그룹을 참조하는 행을 지운 뒤에. */
    protected long createGroup() {
        return userJdbc.sql("INSERT INTO groups (type, name, created_at) VALUES ('FRIEND', '테스트', now()) RETURNING id")
                .query(Long.class)
                .single();
    }

    protected void deleteGroup(long groupId) {
        userJdbc.sql("DELETE FROM groups WHERE id = :id").param("id", groupId).update();
    }
}
