package com.haengunping.mission.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * api.md 5절 응답 모양과 시작을 실제 DB로 확인한다. 배정·크기 규칙은 MissionServiceTest가 맡는다.
 * 920번대 테스트 주제 하나를 넣고 오늘 미션으로 미리 배정해 둔다. 시드 주제가 있어도 응답 값이 정해진다(MissionServiceTest는 910번대).
 */
class MissionControllerTest extends IntegrationTest {

    private static final String TODAY = ApiPaths.V1 + "/missions/today";
    private static final short TOPIC = 921;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;

    private long me;

    @BeforeEach
    void setUp() {
        me = createUser();
        jdbc.update("""
                INSERT INTO mission_topics (id, category, outdoor_text, indoor_text, size_type, priority_moods)
                VALUES (?, 'NATURE_SUN', '햇빛 받으며 걷기', '창가에서 햇빛 쬐기', 'TIME', '{OKAY}'::varchar[])
                """, TOPIC);
        jdbc.update("INSERT INTO daily_missions (user_id, date, topic_id) VALUES (?, ?, ?)", me, LocalDate.now(clock), TOPIC);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM daily_missions WHERE user_id = ?", me);
        jdbc.update("DELETE FROM mission_topics WHERE id = ?", TOPIC);
        deleteUser(me);
    }

    @Test
    void MIS02_오늘_미션을_api_md_모양으로_준다() throws Exception {
        mvc.perform(get(TODAY).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(LocalDate.now(clock).toString()))
                .andExpect(jsonPath("$.topic.topicId").value((int) TOPIC))
                .andExpect(jsonPath("$.topic.category").value("NATURE_SUN"))
                .andExpect(jsonPath("$.topic.outdoorText").value("햇빛 받으며 걷기"))
                .andExpect(jsonPath("$.topic.indoorText").value("창가에서 햇빛 쬐기"))
                .andExpect(jsonPath("$.topic.sizeType").value("TIME"))
                .andExpect(jsonPath("$.defaults.minutes").value(10))
                .andExpect(jsonPath("$.defaults.place").value("OUTDOOR"))
                .andExpect(jsonPath("$.sizeByMinutes.5").exists())
                .andExpect(jsonPath("$.sizeByMinutes.10").exists())
                .andExpect(jsonPath("$.sizeByMinutes.20").exists())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.startedAt").isEmpty())
                .andExpect(jsonPath("$.completedAt").isEmpty())
                .andExpect(jsonPath("$.minutes").isEmpty())
                .andExpect(jsonPath("$.place").isEmpty())
                .andExpect(jsonPath("$.size").isEmpty());
    }

    @Test
    void MIS06_시작하면_고른_값과_시작_시각을_준다() throws Exception {
        mvc.perform(json(post(TODAY + "/start"), """
                        {"minutes":20,"place":"INDOOR","size":20}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.minutes").value(20))
                .andExpect(jsonPath("$.place").value("INDOOR"))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void MIS05_크기가_short를_넘어도_500이_아니라_400() throws Exception {
        mvc.perform(json(post(TODAY + "/start"), """
                        {"minutes":10,"place":"INDOOR","size":40000}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void MIS06_허용되지_않는_시간이면_400() throws Exception {
        mvc.perform(json(post(TODAY + "/start"), """
                        {"minutes":7,"place":"INDOOR","size":7}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void MIS06_장소가_없으면_400() throws Exception {
        mvc.perform(json(post(TODAY + "/start"), """
                        {"minutes":10,"size":10}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void MIS07_다_했어요는_완료_시각과_그룹_배열을_주고_두_번째는_409() throws Exception {
        mvc.perform(json(post(TODAY + "/complete"), ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.groups").isArray());

        mvc.perform(json(post(TODAY + "/complete"), ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_COMPLETED"));
    }

    @Test
    void 내_기록은_api_md_모양으로_준다() throws Exception {
        mvc.perform(json(post(TODAY + "/complete"), "")).andExpect(status().isOk());
        String month = LocalDate.now(clock).toString().substring(0, 7);

        mvc.perform(get(ApiPaths.V1 + "/me/records").param("month", month).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missions[0].date").value(LocalDate.now(clock).toString()))
                .andExpect(jsonPath("$.missions[0].category").value("NATURE_SUN"))
                .andExpect(jsonPath("$.missions[0].text").value("햇빛 받으며 걷기"))
                .andExpect(jsonPath("$.missions[0].sizeType").value("TIME"))
                .andExpect(jsonPath("$.missions[0].size").isEmpty());
    }

    @Test
    void 내_기록_month_형식이_틀리면_400() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/me/records").param("month", "2026-13").with(as(me)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void SEC01_로그인하지_않으면_401() throws Exception {
        mvc.perform(get(TODAY)).andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor as(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    /** 로그인·실제 CSRF 흐름(ErrorResponseTest와 같은 방식)·JSON 본문을 붙인다. */
    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) throws Exception {
        Cookie token = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.with(as(me))
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
