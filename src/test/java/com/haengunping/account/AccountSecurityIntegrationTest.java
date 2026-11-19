package com.haengunping.account;

import com.haengunping.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 Spring Security 필터 체인과 PostgreSQL을 거치는 계정 보안 통합 테스트.
 * IntegrationTest에서 제공하는 Testcontainers PostgreSQL이 필요하다.
 *
 * 모든 계정 생성 요청에 테스트별 고유한 원격 주소를 사용해 인메모리 IP 제한의
 * 교차 테스트 영향을 막는다. 테스트가 만든 사용자 행은 마지막에 정리한다.
 */
@TestPropertySource(properties = {
        "haengunping.rate-limit.account-create.limit=2",
        "haengunping.cookie-secure=true"
})
class AccountSecurityIntegrationTest extends IntegrationTest {

    private static final String SESSION_COOKIE = "__Host-hp_session";

    @Autowired
    JdbcClient jdbc;

    private final List<Long> createdUsers = new ArrayList<>();

    @AfterEach
    void deleteCreatedUsers() {
        for (Long userId : createdUsers) {
            jdbc.sql("DELETE FROM users WHERE id = :id")
                    .param("id", userId)
                    .update();
        }
        createdUsers.clear();
    }

    @Test
    void SEC01_계정생성_시_보안쿠키가_발급되고_본인_조회에_사용된다() throws Exception {
        String nickname = uniqueNickname();
        Cookie session = createAccount(nickname, "192.0.2.101");

        mvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nickname));
    }

    @Test
    void SEC01_세션쿠키가_없거나_무효하면_인증에_실패한다() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/me")
                        .cookie(new Cookie(SESSION_COOKIE, "invalid-session-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void SEC04_CSRF_토큰이_없으면_상태_변경을_거부한다() throws Exception {
        String nickname = uniqueNickname();
        mvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNickname(nickname)))
                .andExpect(status().isForbidden());

        Cookie session = createAccount(nickname, "192.0.2.102");
        mvc.perform(patch("/api/v1/me")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNickname("Changed")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nickname));
    }

    @Test
    void SEC08_동일_IP_계정생성_한도_초과_시_429를_반환한다() throws Exception {
        String remoteAddr = "192.0.2.103";
        createAccount(uniqueNickname(), remoteAddr);
        createAccount(uniqueNickname(), remoteAddr);

        mvc.perform(post("/api/v1/accounts")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(remoteAddr);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNickname(uniqueNickname())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void ACC10_계정삭제_후_기존_세션으로는_접근할_수_없다() throws Exception {
        Cookie session = createAccount(uniqueNickname(), "192.0.2.104");

        MvcResult deleted = mvc.perform(delete("/api/v1/me")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn();

        String expiredCookie = deleted.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(expiredCookie, "탈퇴 시 세션 쿠키 만료 헤더가 있어야 합니다");
        assertTrue(expiredCookie.startsWith(SESSION_COOKIE + "="), expiredCookie);
        assertTrue(expiredCookie.contains("Max-Age=0"), expiredCookie);

        mvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    /** 정상 HTTP 가입을 거쳐 발급된 쿠키를 재사용한다. */
    private Cookie createAccount(String nickname, String remoteAddr) throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/accounts")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(remoteAddr);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNickname(nickname)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andReturn();

        // 신규 계정은 닉네임이 고유한 테스트 데이터이므로 DB에서 ID를 확인해 정리한다.
        Long userId = jdbc.sql("SELECT id FROM users WHERE nickname = :nickname")
                .param("nickname", nickname)
                .query(Long.class)
                .single();
        createdUsers.add(userId);

        String setCookie = created.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "계정 생성 응답에 Set-Cookie가 있어야 합니다");
        assertTrue(setCookie.startsWith(SESSION_COOKIE + "="), setCookie);
        assertTrue(setCookie.contains("Secure"), setCookie);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
        assertTrue(setCookie.contains("Path=/"), setCookie);

        String rawToken = setCookie.substring((SESSION_COOKIE + "=").length()).split(";", 2)[0];
        return new Cookie(SESSION_COOKIE, rawToken);
    }

    private static String uniqueNickname() {
        // 닉네임 최대 10자. UUID 일부를 사용해 테스트 데이터끼리 충돌하지 않도록 한다.
        return "IT" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String jsonNickname(String nickname) {
        return "{\"nickname\":\"" + nickname + "\"}";
    }
}
