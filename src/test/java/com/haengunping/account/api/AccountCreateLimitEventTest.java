package com.haengunping.account.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** DEMO-05: 행사 설정(한도 20)에서는 같은 IP의 11번째 가입이 201이고, 21번째는 다시 429다. */
@TestPropertySource(properties = "haengunping.rate-limit.account-create.limit=20")
class AccountCreateLimitEventTest extends IntegrationTest {

    @Test
    void DEMO05_행사_설정에서_같은_IP_11번째_가입은_201() throws Exception {
        for (int i = 1; i <= 20; i++) {
            createAccount("행사" + i).andExpect(status().isCreated());
        }
        createAccount("행사21").andExpect(status().isTooManyRequests());
    }

    private org.springframework.test.web.servlet.ResultActions createAccount(String nickname) throws Exception {
        Cookie xsrf = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return mvc.perform(post(ApiPaths.V1 + "/accounts")
                .with(request -> {
                    request.setRemoteAddr("198.51.100.7");
                    return request;
                })
                .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"));
    }
}
