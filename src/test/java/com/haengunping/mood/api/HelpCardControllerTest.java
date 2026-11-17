package com.haengunping.mood.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.mood.Mood;
import com.haengunping.mood.infra.HelpCardDismissalRepository;
import com.haengunping.mood.infra.MoodRepository;
import com.haengunping.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/** api.md 4절 도움 카드 응답 모양과 닫기를 실제 DB로 확인한다. 표시 규칙 자체는 HelpCardServiceTest가 맡는다. */
class HelpCardControllerTest extends IntegrationTest {

    private static final String HELP_CARD = ApiPaths.V1 + "/help-card";

    @Autowired
    MoodRepository moods;
    @Autowired
    HelpCardDismissalRepository dismissals;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    Clock clock;

    private long me;

    @BeforeEach
    void createMe() {
        me = createUser();
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(s -> {
            moods.deleteByUserId(me);
            dismissals.deleteById(me);
        });
        deleteUser(me); // JPA 삭제가 커밋으로 반영된 뒤에 지워야 외래 키에 걸리지 않는다
    }

    @Test
    void MOOD04_낮은_기분_3일이면_보이고_닫으면_숨는다() throws Exception {
        LocalDate today = LocalDate.now(clock);
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < 3; i++) {
                moods.upsert(me, today.minusDays(i), Mood.TIRED.name(), clock.instant());
            }
        });

        mvc.perform(get(HELP_CARD).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.show").value(true))
                .andExpect(jsonPath("$.reason").value("LOW_MOOD_3_DAYS"));

        mvc.perform(post(HELP_CARD + "/dismiss").with(as(me)).with(realCsrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get(HELP_CARD).with(as(me)))
                .andExpect(jsonPath("$.show").value(false))
                .andExpect(jsonPath("$.reason").isEmpty());
    }

    @Test
    void MOOD04_기록이_없으면_보이지_않는다() throws Exception {
        mvc.perform(get(HELP_CARD).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.show").value(false));
    }

    @Test
    void SEC01_로그인하지_않으면_401() throws Exception {
        mvc.perform(get(HELP_CARD)).andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor as(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    /** 프론트와 같은 CSRF 흐름(ErrorResponseTest 참고). 테스트 도구의 csrf()는 쓰지 않는다. */
    private RequestPostProcessor realCsrf() throws Exception {
        Cookie token = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request -> {
            request.setCookies(token);
            request.addHeader("X-XSRF-TOKEN", token.getValue());
            return request;
        };
    }
}
