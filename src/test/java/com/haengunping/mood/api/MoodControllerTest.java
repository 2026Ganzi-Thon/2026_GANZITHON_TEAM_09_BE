package com.haengunping.mood.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** api.md 4절 기분 API를 실제 DB로 확인한다. */
class MoodControllerTest extends IntegrationTest {

    private static final String TODAY = ApiPaths.V1 + "/moods/today";
    private static final String MOODS = ApiPaths.V1 + "/moods";

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;

    private long me;
    private LocalDate today;

    @BeforeEach
    void createMe() {
        me = createUser();
        today = LocalDate.now(clock);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM moods WHERE user_id = ?", me);
        deleteUser(me);
    }

    @Test
    void MOOD02_같은_날_두_번_저장하면_1건_나중_값() throws Exception {
        consent();
        mvc.perform(json(put(TODAY), "{\"mood\":\"LONELY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.mood").value("LONELY"));
        mvc.perform(json(put(TODAY), "{\"mood\":\"CALM\"}")).andExpect(status().isOk());

        mvc.perform(get(MOODS).param("from", today.toString()).param("to", today.toString()).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moods.length()").value(1))
                .andExpect(jsonPath("$.moods[0].date").value(today.toString()))
                .andExpect(jsonPath("$.moods[0].mood").value("CALM"));
    }

    @Test
    void ACC05_동의하지_않으면_403() throws Exception {
        mvc.perform(json(put(TODAY), "{\"mood\":\"LONELY\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void MOOD01_모르는_기분_값이나_빈_값은_400() throws Exception {
        consent();
        // 모르는 값·숫자·빈 값 모두 같은 코드와 필드 이름(api.md 공통 규칙, 2026-11-18 확정)
        for (String body : new String[] {"{\"mood\":\"HAPPY\"}", "{\"mood\":3}"}) {
            mvc.perform(json(put(TODAY), body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fields[0]").value("mood"));
        }
        mvc.perform(json(put(TODAY), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void MOOD05_93일은_되고_94일이면_400() throws Exception {
        mvc.perform(get(MOODS).param("from", today.minusDays(92).toString()).param("to", today.toString()).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moods.length()").value(0));
        mvc.perform(get(MOODS).param("from", today.minusDays(93).toString()).param("to", today.toString()).with(as(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void SEC01_로그인하지_않으면_401() throws Exception {
        mvc.perform(get(MOODS).param("from", today.toString()).param("to", today.toString()))
                .andExpect(status().isUnauthorized());
    }

    private void consent() {
        jdbc.update("UPDATE users SET sensitive_consent_at = now() WHERE id = ?", me);
    }

    private static RequestPostProcessor as(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    /** 로그인·실제 CSRF 흐름·JSON 본문을 붙인다(NotificationControllerTest와 같은 방식). */
    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) throws Exception {
        Cookie token = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.with(as(me))
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
