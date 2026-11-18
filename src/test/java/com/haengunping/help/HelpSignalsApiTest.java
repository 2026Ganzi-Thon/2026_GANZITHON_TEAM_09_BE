package com.haengunping.help;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import com.haengunping.support.MutableClock;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * {@code GET /help/signals}(HELP-06, api.md 8절)를 실제 DB와 앱 전체로 확인한다. 시각은 {@link MutableClock}.
 * 2026-11-22(일) 20:00 KST 기준, 7일 전 경계는 11-15 20:00.
 */
class HelpSignalsApiTest extends IntegrationTest {

    private static final String SIGNALS = ApiPaths.V1 + "/help/signals";
    private static final String NOW = "2026-11-22T20:00:00";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MutableClock clock;

    @Value("${haengunping.session-cookie-name}")
    String sessionCookieName;

    private long me;

    @BeforeEach
    void setUp() {
        me = createUser();
        clock.set(NOW);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM checks WHERE user_id = ?", me);
        jdbc.update("DELETE FROM moods WHERE user_id = ?", me);
        deleteUser(me);
    }

    @Test
    void 쿠키가_없으면_401이_아니라_둘_다_false로_200() throws Exception {
        mvc.perform(get(SIGNALS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lonely3Days").value(false))
                .andExpect(jsonPath("$.checkAboveThreshold").value(false));
    }

    @Test
    void 무효_쿠키도_401이_아니라_둘_다_false로_200() throws Exception {
        mvc.perform(get(SIGNALS).cookie(new Cookie(sessionCookieName, "not-a-valid-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lonely3Days").value(false))
                .andExpect(jsonPath("$.checkAboveThreshold").value(false));
    }

    @Test
    void 기록이_없으면_둘_다_false() throws Exception {
        mvc.perform(get(SIGNALS).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lonely3Days").value(false))
                .andExpect(jsonPath("$.checkAboveThreshold").value(false));
    }

    @Test
    void 외로워요_3일_연속이면_lonely3Days가_true이고_점검_신호는_false() throws Exception {
        lonely("2026-11-20", "2026-11-21", "2026-11-22");

        mvc.perform(get(SIGNALS).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lonely3Days").value(true))
                .andExpect(jsonPath("$.checkAboveThreshold").value(false));
    }

    @Test
    void 외로워요가_이틀뿐이면_lonely3Days는_false() throws Exception {
        lonely("2026-11-21", "2026-11-22");

        mvc.perform(get(SIGNALS).with(as(me)))
                .andExpect(jsonPath("$.lonely3Days").value(false));
    }

    @Test
    void 다른_사람의_외로움_기록은_내_신호에_섞이지_않는다() throws Exception {
        long other = createUser();
        try {
            for (String d : List.of("2026-11-20", "2026-11-21", "2026-11-22")) {
                jdbc.update("INSERT INTO moods (user_id, date, mood, updated_at) VALUES (?, DATE '" + d + "', 'LONELY', now())", other);
            }
            mvc.perform(get(SIGNALS).with(as(me)))
                    .andExpect(jsonPath("$.lonely3Days").value(false));
        } finally {
            jdbc.update("DELETE FROM moods WHERE user_id = ?", other);
            deleteUser(other);
        }
    }

    @Test
    void 엿새_전_우울_제출은_checkAboveThreshold가_true() throws Exception {
        done("2026-11-16", "ARRAY['DEPRESSION']", "TIMESTAMPTZ '2026-11-16 20:00:00+09'");

        mvc.perform(get(SIGNALS).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkAboveThreshold").value(true))
                .andExpect(jsonPath("$.lonely3Days").value(false));
    }

    @Test
    void 여드레_전_우울_제출은_7일_밖이라_false() throws Exception {
        done("2026-11-09", "ARRAY['DEPRESSION']", "TIMESTAMPTZ '2026-11-14 20:00:00+09'");

        mvc.perform(get(SIGNALS).with(as(me)))
                .andExpect(jsonPath("$.checkAboveThreshold").value(false));
    }

    @Test
    void 최근_제출이어도_기준_미만이면_false() throws Exception {
        done("2026-11-16", "ARRAY['STABLE']", "TIMESTAMPTZ '2026-11-20 20:00:00+09'");

        mvc.perform(get(SIGNALS).with(as(me)))
                .andExpect(jsonPath("$.checkAboveThreshold").value(false));
    }

    @Test
    void 점검을_닫기만_한_주는_false() throws Exception {
        jdbc.update("INSERT INTO checks (user_id, week_start, status, result_codes, created_at) "
                + "VALUES (?, DATE '2026-11-16', 'DISMISSED', NULL, TIMESTAMPTZ '2026-11-20 20:00:00+09')", me);

        mvc.perform(get(SIGNALS).with(as(me)))
                .andExpect(jsonPath("$.checkAboveThreshold").value(false));
    }

    @Test
    void 응답_키는_두_개뿐() throws Exception {
        lonely("2026-11-20", "2026-11-21", "2026-11-22");

        mvc.perform(get(SIGNALS).with(as(me)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.lonely3Days").exists())
                .andExpect(jsonPath("$.checkAboveThreshold").exists());
    }

    @Test
    void permitAll은_GET만_POST와_PUT은_인증을_요구한다() throws Exception {
        // CSRF를 맞춘 요청으로 보내야 403(CSRF)에 가려지지 않고 인가 규칙까지 닿는다
        Cookie xsrf = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post(SIGNALS).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(SIGNALS).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    private void lonely(String... dates) {
        for (String d : dates) {
            jdbc.update("INSERT INTO moods (user_id, date, mood, updated_at) VALUES (?, DATE '" + d + "', 'LONELY', now())", me);
        }
    }

    private void done(String weekStart, String codes, String createdAt) {
        jdbc.update("INSERT INTO checks (user_id, week_start, status, result_codes, created_at) "
                + "VALUES (?, DATE '" + weekStart + "', 'DONE', " + codes + "::varchar[], " + createdAt + ")", me);
    }

    private static RequestPostProcessor as(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
