package com.haengunping.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * SEC-11: 로그에 세션 토큰·기분·점검 답을 남기지 않는다(SECURITY.md T10).
 * 앱에는 요청 로그가 없고 Caddy도 접근 로그를 켜지 않는다. 남는 길은 예외 메시지다:
 * PostgreSQL은 제약 위반 때 "Failing row contains (...)"로 행 전체를 메시지에 담고,
 * 그 메시지가 Hibernate 오류 로그와 GlobalExceptionHandler의 500 로그에 그대로 찍힌다.
 */
@ExtendWith(OutputCaptureExtension.class)
class SensitiveLogTest extends IntegrationTest {

    @Value("${haengunping.session-cookie-name}")
    String sessionCookieName;

    @Autowired
    JdbcClient jdbc;

    @Test
    void SEC11_DB_제약_위반_오류_메시지에_행의_값이_실리지_않는다() {
        long userId = createUser();
        try {
            // 앱의 검증을 거치지 않은 값이 DB 제약에 걸린 상황. 앱처럼 값은 바인딩 파라미터로 보낸다.
            // 값이 메시지에 실리면 Hibernate 오류 로그와 500 로그로 남는다.
            Throwable error = catchThrowable(() -> jdbc
                    .sql("INSERT INTO moods (user_id, date, mood, updated_at) VALUES (:u, DATE '2026-11-18', :m, now())")
                    .param("u", userId)
                    .param("m", "XSECRETX")
                    .update());

            assertThat(error).isNotNull();
            assertThat(chainText(error))
                    .contains("moods_mood_check") // 어느 제약인지는 남아야 원인을 찾는다
                    .doesNotContain("XSECRETX");
        } finally {
            deleteUser(userId);
        }
    }

    @Test
    void SEC11_가입부터_기분_저장과_잘못된_요청까지_로그에_토큰과_기분이_없다(CapturedOutput output) throws Exception {
        Cookie xsrf = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        Cookie session = mvc.perform(post(ApiPaths.V1 + "/accounts")
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"로그검사\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie(sessionCookieName);

        mvc.perform(put(ApiPaths.V1 + "/me/consents/sensitive")
                        .cookie(session, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(put(ApiPaths.V1 + "/moods/today")
                        .cookie(session, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mood\":\"LONELY\"}"))
                .andExpect(status().isOk());
        // 잘못된 요청도 본문 값을 로그로 되풀이하지 않는다
        mvc.perform(put(ApiPaths.V1 + "/moods/today")
                        .cookie(session, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mood\":\"QSECRETQ\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ApiPaths.V1 + "/moods/today")
                        .cookie(session, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mood\":\"WSECRETW\""))
                .andExpect(status().isBadRequest());

        assertThat(output.getAll())
                .doesNotContain(session.getValue())
                .doesNotContain("QSECRETQ")
                .doesNotContain("WSECRETW")
                .doesNotContainPattern("(?i)mood[^\\n]{0,40}LONELY");
    }

    private static String chainText(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            text.append(t).append('\n');
        }
        return text.toString();
    }
}
