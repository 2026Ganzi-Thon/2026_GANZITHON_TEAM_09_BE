package com.haengunping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/** 실제 PostgreSQL에 Flyway를 적용하고 앱 전체가 뜨는지 확인한다. */
class ApplicationStartupTest extends IntegrationTest {

    @Test
    void 앱이_PostgreSQL과_함께_뜨고_상태_확인에_답한다() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
