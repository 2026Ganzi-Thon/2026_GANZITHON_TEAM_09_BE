package com.haengunping.notification.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class PushControllerTest extends IntegrationTest {

    @Test
    void NOTI01_VAPID_공개키는_쿠키_없이_받는다() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/push/vapid-public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").exists());
    }

    @Test
    void SEC01_공개키_밖의_push_경로는_쿠키가_필요하다() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/push/other"))
                .andExpect(status().isUnauthorized());
    }
}
