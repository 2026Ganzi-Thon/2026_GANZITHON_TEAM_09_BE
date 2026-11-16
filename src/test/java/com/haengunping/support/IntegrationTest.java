package com.haengunping.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
}
