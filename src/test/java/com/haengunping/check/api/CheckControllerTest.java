package com.haengunping.check.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import com.haengunping.support.MutableClock;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * api.md 7절 주간 점검 API를 실제 DB와 앱 전체로 확인한다. 시각은 {@link MutableClock}으로 정한다.
 * 2026-11-22는 일요일이고 그 주 월요일은 2026-11-16이다.
 */
@ExtendWith(OutputCaptureExtension.class)
class CheckControllerTest extends IntegrationTest {

    private static final String CURRENT = ApiPaths.V1 + "/checks/current";
    private static final String CHECKS = ApiPaths.V1 + "/checks";
    private static final String DISMISS = ApiPaths.V1 + "/checks/current/dismiss";
    private static final String SUNDAY_EVENING = "2026-11-22T20:00:00";
    private static final String MONDAY = "2026-11-16";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(SUNDAY_EVENING);
        }
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MutableClock clock;

    private final JsonMapper json = JsonMapper.builder().build();
    private long me;

    @BeforeEach
    void setUp() {
        me = createUser();
        clock.set(SUNDAY_EVENING);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM checks WHERE user_id = ?", me);
        jdbc.update("DELETE FROM moods WHERE user_id = ?", me);
        deleteUser(me);
    }

    // ---- GET /checks/current (CHK-01)

    @Test
    void CHK01_일요일_17시59분은_due가_아니고_18시는_due() throws Exception {
        clock.set("2026-11-22T17:59:59");
        mvc.perform(get(CURRENT).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value(MONDAY))
                .andExpect(jsonPath("$.due").value(false))
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.dismissed").value(false));

        clock.set("2026-11-22T18:00:00");
        mvc.perform(get(CURRENT).with(as(me)))
                .andExpect(jsonPath("$.due").value(true));

        clock.set("2026-11-22T23:59:59");
        mvc.perform(get(CURRENT).with(as(me)))
                .andExpect(jsonPath("$.due").value(true));

        clock.set("2026-11-23T00:00:00");
        mvc.perform(get(CURRENT).with(as(me)))
                .andExpect(jsonPath("$.weekStart").value("2026-11-23"))
                .andExpect(jsonPath("$.due").value(false));
    }

    @Test
    void CHK01_동의하지_않아도_상태는_볼_수_있다() throws Exception {
        mvc.perform(get(CURRENT).with(as(me))).andExpect(status().isOk());
    }

    @Test
    void SEC01_로그인하지_않으면_세_API_모두_401() throws Exception {
        mvc.perform(get(CURRENT)).andExpect(status().isUnauthorized());
        mvc.perform(post(CHECKS).with(csrfFor(null)).contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[0,0,0,0]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(DISMISS).with(csrfFor(null))).andExpect(status().isUnauthorized());
    }

    // ---- POST /checks (CHK-02~06)

    @Test
    void CHK03_04_06_우울_결과_201과_점수_없는_응답() throws Exception {
        consent();

        MvcResult result = mvc.perform(submit("[2,1,0,0]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0]").value("DEPRESSION"))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.messages[0]").value("요즘 많이 지쳐 있었던 것 같아요"))
                .andExpect(jsonPath("$.notice").value("진단이 아니라 참고예요"))
                .andExpect(jsonPath("$.sections[0]").value("CRISIS_24H"))
                .andExpect(jsonPath("$.sections[1]").value("COUNSELING"))
                .andReturn();

        // CHK-04: 점수 칸이 없다. 응답 키는 이 넷뿐이다.
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        List<String> keys = new ArrayList<>();
        body.propertyNames().forEach(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("results", "messages", "notice", "sections");
    }

    @Test
    void CHK03_우울과_불안이_둘_다면_둘_다_돌려준다() throws Exception {
        consent();

        mvc.perform(submit("[2,1,3,0]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0]").value("DEPRESSION"))
                .andExpect(jsonPath("$.results[1]").value("ANXIETY"))
                .andExpect(jsonPath("$.messages[1]").value("걱정이 많았던 한 주였던 것 같아요"));
    }

    @Test
    void CHK03_낮은_기분_4일이면_LOW_MOOD_DAYS_3일이면_STABLE() throws Exception {
        consent();
        lowMoods(3);
        mvc.perform(submit("[1,1,1,1]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0]").value("STABLE"))
                .andExpect(jsonPath("$.sections.length()").value(0));
        jdbc.update("DELETE FROM checks WHERE user_id = ?", me);

        lowMoods(4);
        mvc.perform(submit("[1,1,1,1]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0]").value("LOW_MOOD_DAYS"))
                .andExpect(jsonPath("$.messages[0]").value("기분이 가라앉은 날이 많았네요"))
                .andExpect(jsonPath("$.sections[0]").value("DROP_IN"));
    }

    @Test
    void CHK03_다른_주의_낮은_기분은_세지_않는다() throws Exception {
        consent();
        for (int i = 0; i < 4; i++) { // 지난주(11/9~11/15)에 낮은 기분 4일
            jdbc.update("INSERT INTO moods (user_id, date, mood, updated_at) VALUES (?, DATE '2026-11-09' + ?, 'LONELY', now())", me, i);
        }

        mvc.perform(submit("[1,1,1,1]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0]").value("STABLE"));
    }

    @Test
    void CHK05_같은_주_두_번째_제출은_409() throws Exception {
        consent();
        mvc.perform(submit("[0,0,0,0]")).andExpect(status().isCreated());

        mvc.perform(submit("[0,0,0,0]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECK_NOT_DUE"));
        assertThat(count()).isOne();

        mvc.perform(get(CURRENT).with(as(me)))
                .andExpect(jsonPath("$.due").value(false))
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.dismissed").value(false));
    }

    @Test
    void CHK05_창이_닫혀_있으면_409_이고_기록하지_않는다() throws Exception {
        consent();
        clock.set("2026-11-22T17:59:59");

        mvc.perform(submit("[2,1,0,0]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECK_NOT_DUE"));
        assertThat(count()).isZero();
    }

    @Test
    void CHK05_일요일_밤_23시59분의_제출은_그_주_월요일로_기록된다() throws Exception {
        consent();
        clock.set("2026-11-22T23:59:59");

        mvc.perform(submit("[0,0,0,0]")).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT week_start::text FROM checks WHERE user_id = ?", String.class, me))
                .isEqualTo(MONDAY);
    }

    @Test
    void CHK05_다음_주에는_다시_할_수_있다() throws Exception {
        consent();
        mvc.perform(submit("[0,0,0,0]")).andExpect(status().isCreated());

        clock.set("2026-11-29T18:00:00");
        mvc.perform(get(CURRENT).with(as(me)))
                .andExpect(jsonPath("$.weekStart").value("2026-11-23"))
                .andExpect(jsonPath("$.due").value(true))
                .andExpect(jsonPath("$.done").value(false));
        mvc.perform(submit("[0,0,0,0]")).andExpect(status().isCreated());
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void ACC05_동의하지_않으면_403_이고_형식이_틀려도_403이_먼저다() throws Exception {
        mvc.perform(submit("[0,0,0,0]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mvc.perform(submit("[9]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        assertThat(count()).isZero();
    }

    @Test
    void CHK02_답_형식이_틀리면_400과_칸_이름() throws Exception {
        consent();

        mvc.perform(submit("[0,0,0]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("answers"));
        mvc.perform(submit("[0,0,0,0,0]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("answers"));
        mvc.perform(submit("[0,0,0,4]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("answers[3]"));
        mvc.perform(submit("[-1,0,0,0]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("answers[0]"));
        mvc.perform(submit("[0,null,0,0]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("answers[1]"));
        mvc.perform(submit("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("answers"));
        assertThat(count()).isZero();
    }

    @Test
    void CHK02_answers가_없거나_null이면_400() throws Exception {
        consent();

        mvc.perform(post(CHECKS).with(as(me)).with(csrfFor(me)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("answers"));
        mvc.perform(post(CHECKS).with(as(me)).with(csrfFor(me)).contentType(MediaType.APPLICATION_JSON).content("{\"answers\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("answers"));
    }

    @Test
    void CHK02_숫자가_아닌_답이나_깨진_본문은_MALFORMED_REQUEST() throws Exception {
        consent();

        for (String body : new String[] {"{\"answers\":[\"a\",0,0,0]}", "{\"answers\":\"0000\"}", "{\"answers\":", "[0,0,0,0]"}) {
            mvc.perform(post(CHECKS).with(as(me)).with(csrfFor(me)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }
        assertThat(count()).isZero();
    }

    @Test
    void CHK02_소수_답은_거부한다() throws Exception {
        consent();

        // 2.5가 2로 잘려 들어가면 판정이 달라진다(application.yml accept-float-as-int). 숫자 형식 오류라 MALFORMED_REQUEST.
        for (String body : new String[] {"{\"answers\":[2.5,0,0,0]}", "{\"answers\":[2.0,0,0,0]}"}) {
            mvc.perform(post(CHECKS).with(as(me)).with(csrfFor(me)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }
        assertThat(count()).isZero();
    }

    // ---- POST /checks/current/dismiss

    @Test
    void 닫으면_204_이고_그_주에는_due가_아니며_제출은_409() throws Exception {
        consent();

        mvc.perform(dismiss()).andExpect(status().isNoContent());

        mvc.perform(get(CURRENT).with(as(me)))
                .andExpect(jsonPath("$.due").value(false))
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.dismissed").value(true));
        mvc.perform(submit("[2,1,0,0]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECK_NOT_DUE"));
    }

    @Test
    void 닫기는_동의_없이도_되고_창과_무관하게_204_이며_여러_번_해도_한_건() throws Exception {
        clock.set("2026-11-18T12:00:00"); // 수요일, 창 밖

        mvc.perform(dismiss()).andExpect(status().isNoContent());
        mvc.perform(dismiss()).andExpect(status().isNoContent());

        assertThat(count()).isOne();
    }

    @Test
    void 이미_제출한_주에_닫아도_제출_기록은_그대로() throws Exception {
        consent();
        mvc.perform(submit("[2,1,0,0]")).andExpect(status().isCreated());

        mvc.perform(dismiss()).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT status FROM checks WHERE user_id = ?", String.class, me)).isEqualTo("DONE");
    }

    // ---- 동시성, 본인 데이터

    @Test
    void CHK05_동시에_여러_번_제출해도_한_건만_들어간다() throws Exception {
        consent();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(submit("[2,1,0,0]")).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(threads - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count()).isOne();
    }

    @Test
    void 다른_사람의_기록은_내_상태에_영향이_없다() throws Exception {
        long other = createUser();
        try {
            jdbc.update("INSERT INTO checks (user_id, week_start, status, result_codes, created_at) "
                    + "VALUES (?, DATE '2026-11-16', 'DONE', ARRAY['STABLE']::varchar[], now())", other);

            mvc.perform(get(CURRENT).with(as(me)))
                    .andExpect(jsonPath("$.due").value(true))
                    .andExpect(jsonPath("$.done").value(false));
        } finally {
            jdbc.update("DELETE FROM checks WHERE user_id = ?", other);
            deleteUser(other);
        }
    }

    @Test
    void D13_답은_저장하지_않는다_checks에_answers_칸이_없다() {
        Integer columns = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'checks' AND column_name LIKE '%answer%'",
                Integer.class);

        assertThat(columns).isZero();
    }

    @Test
    void SEC11_점검_흐름의_로그에_답과_결과가_없다(CapturedOutput output) throws Exception {
        consent();
        mvc.perform(submit("[2,1,3,0]")).andExpect(status().isCreated()); // 우울+불안
        mvc.perform(submit("[2,1,3,0]")).andExpect(status().isConflict());
        mvc.perform(submit("[0,0,0,9]")).andExpect(status().isBadRequest()); // 틀린 답도 로그로 되풀이하지 않는다
        mvc.perform(dismiss()).andExpect(status().isNoContent());

        assertThat(output.getAll())
                .doesNotContain("DEPRESSION")
                .doesNotContain("ANXIETY")
                .doesNotContain("LOW_MOOD_DAYS")
                .doesNotContain("[2,1,3,0]")
                .doesNotContain("[0,0,0,9]")
                .doesNotContainPattern("(?i)answers");
    }

    // ---- helpers

    private void consent() {
        jdbc.update("UPDATE users SET sensitive_consent_at = now() WHERE id = ?", me);
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM checks WHERE user_id = ?", Integer.class, me);
    }

    /** 이번 주(11/16~)에 낮은 기분을 n일 남긴다. */
    private void lowMoods(int days) {
        for (int i = 0; i < days; i++) {
            jdbc.update("INSERT INTO moods (user_id, date, mood, updated_at) VALUES (?, DATE '2026-11-16' + ?, 'TIRED', now()) "
                    + "ON CONFLICT (user_id, date) DO NOTHING", me, i);
        }
    }

    private MockHttpServletRequestBuilder submit(String answers) throws Exception {
        return post(CHECKS).with(as(me)).with(csrfFor(me)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":" + answers + "}");
    }

    private MockHttpServletRequestBuilder dismiss() throws Exception {
        return post(DISMISS).with(as(me)).with(csrfFor(me));
    }

    private static RequestPostProcessor as(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    /** 실제 CSRF 흐름(GET으로 쿠키를 받고 헤더로 돌려보냄). 로그인 여부와 무관하게 붙인다. */
    private RequestPostProcessor csrfFor(Long ignored) {
        return request -> {
            try {
                Cookie token = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
                request.setCookies(token);
                request.addHeader("X-XSRF-TOKEN", token.getValue());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return request;
        };
    }
}
