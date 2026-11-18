package com.haengunping.account.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** SEC-08: 기본 설정에서는 같은 IP의 11번째 계정 생성이 429다. */
class AccountCreateLimitTest extends IntegrationTest {

    @Test
    void SEC08_기본_설정에서_같은_IP_11번째_가입은_429와_Retry_After() throws Exception {
        for (int i = 1; i <= 10; i++) {
            createAccount("203.0.113.10", "기본" + i).andExpect(status().isCreated());
        }
        createAccount("203.0.113.10", "기본11").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // 다른 IP는 영향 없다
        createAccount("203.0.113.11", "다른IP").andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions createAccount(String ip, String nickname) throws Exception {
        Cookie xsrf = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return mvc.perform(post(ApiPaths.V1 + "/accounts")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"));
    }
}
