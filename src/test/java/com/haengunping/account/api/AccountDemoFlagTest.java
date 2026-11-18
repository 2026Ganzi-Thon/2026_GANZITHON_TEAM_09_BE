package com.haengunping.account.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

/**
 * 실서버에는 시연 시계가 없고(D-12), 누구도 스스로 시연 계정이 될 수 없다(SEC-13).
 * 프론트는 GET /me의 isDemo로 시연 도구를 보일지 정하므로, 실서버에서 이 값은 항상 false여야 한다.
 */
class AccountDemoFlagTest extends IntegrationTest {

    @Value("${haengunping.session-cookie-name}")
    String sessionCookieName;

    @Test
    void SEC13_계정_생성_본문에_isDemo를_넣어도_GET_me의_isDemo는_false() throws Exception {
        Cookie xsrf = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        Cookie session = mvc.perform(post(ApiPaths.V1 + "/accounts")
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"시연흉내\",\"isDemo\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie(sessionCookieName);

        mvc.perform(get(ApiPaths.V1 + "/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDemo").value(false));

        // 시계 API는 실서버에 없다: 로그인한 사용자가 CSRF까지 맞춰 불러도 404
        mvc.perform(put(ApiPaths.V1 + "/demo/clock")
                        .cookie(session, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"now\":\"2026-11-22T10:00:00Z\"}"))
                .andExpect(status().isNotFound());
    }
}
