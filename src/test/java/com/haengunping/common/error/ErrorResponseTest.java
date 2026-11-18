package com.haengunping.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.security.SecurityConfig;
import com.haengunping.common.web.ApiPaths;
import com.haengunping.common.web.HealthController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예상 밖 요청도 {@code {code, message}}로 답하는지(SEC-12), 시큐리티 단계 거절도 같은 모양인지(SEC-01·04) 확인한다.
 * DB·Docker 없이 돈다.
 */
// 컨트롤러를 지정하지 않으면 모든 모듈의 컨트롤러가 올라와 그 서비스 빈까지 요구한다.
@WebMvcTest(controllers = HealthController.class)
@Import({SecurityConfig.class, ErrorResponseTest.ProbeController.class})
class ErrorResponseTest {

    private static final String PROBE = ApiPaths.V1 + "/_probe";

    @Autowired
    MockMvc mvc;

    /**
     * 프론트와 같은 방식으로 CSRF 토큰을 붙인다: GET으로 받은 {@code XSRF-TOKEN} 쿠키 값을 {@code X-XSRF-TOKEN} 헤더에 넣는다.
     * 테스트 도구의 {@code csrf()}는 쓰지 않는다. 한 번 쓰면 같은 컨텍스트의 CSRF 저장소가 테스트용으로 바뀐 채 남아,
     * 뒤 테스트에서 쿠키 발급이 사라진다(2026-11-16 확인).
     */
    private RequestPostProcessor realCsrf() throws Exception {
        Cookie token = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request -> {
            request.setCookies(token);
            request.addHeader("X-XSRF-TOKEN", token.getValue());
            return request;
        };
    }

    @Test
    @WithMockUser
    void SEC04_쿠키_값을_헤더로_보내면_CSRF를_통과한다() throws Exception {
        mvc.perform(post(PROBE + "/body").with(realCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"은우\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("은우"));
    }

    @Test
    @WithMockUser
    void SEC04_헤더_값이_쿠키와_다르면_403_CSRF_INVALID() throws Exception {
        Cookie token = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post(PROBE + "/body")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", "forged-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"은우\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void SEC04_첫_GET_응답에_XSRF_TOKEN_쿠키가_실린다() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/health"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().path("XSRF-TOKEN", "/"));
    }

    @Test
    void SEC04_토큰_없는_가입_요청은_403_CSRF_INVALID() throws Exception {
        mvc.perform(post(ApiPaths.V1 + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"은우\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void SEC01_세션_없이_보호된_API를_부르면_401_UNAUTHENTICATED() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser
    void SEC12_깨진_JSON은_400_MALFORMED_REQUEST() throws Exception {
        mvc.perform(post(PROBE + "/body").with(realCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // 열거값 오류는 api.md 공통 규칙대로 VALIDATION_FAILED + 필드 이름이다(2026-11-18 확정).
    // 깨진 JSON·숫자 자리에 문자열 같은 "읽을 수 없는" 요청만 MALFORMED_REQUEST다.
    @Test
    @WithMockUser
    void SEC05_정의_밖_열거값은_400_VALIDATION_FAILED와_필드_이름() throws Exception {
        mvc.perform(post(PROBE + "/choice").with(realCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"color\":\"GREEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("color"));
    }

    @Test
    @WithMockUser
    void SEC05_목록_안의_열거값_오류는_경로까지_필드_이름에_담는다() throws Exception {
        mvc.perform(post(PROBE + "/choices").with(realCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"color\":\"RED\"},{\"color\":\"red\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("items[1].color"));
    }

    @Test
    @WithMockUser
    void SEC05_열거값_자리의_숫자는_순서로_받지_않고_400() throws Exception {
        mvc.perform(post(PROBE + "/choice").with(realCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"color\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("color"));
    }

    @Test
    @WithMockUser
    void SEC05_쿼리_파라미터의_정의_밖_열거값도_400_VALIDATION_FAILED() throws Exception {
        mvc.perform(get(PROBE + "/choice").param("color", "GREEN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("color"));
    }

    // 짝: 열거값이 아닌 타입 불일치는 그대로 MALFORMED_REQUEST다. 이게 없으면 "400이면 전부 VALIDATION_FAILED"와 구별되지 않는다.
    @Test
    @WithMockUser
    void SEC12_숫자_자리의_문자열은_400_MALFORMED_REQUEST() throws Exception {
        mvc.perform(post(PROBE + "/choice").with(realCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"color\":\"RED\",\"count\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.fields").doesNotExist());
        mvc.perform(get(PROBE + "/count").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @WithMockUser
    void SEC05_11자_닉네임은_400_VALIDATION_FAILED와_필드_이름() throws Exception {
        mvc.perform(post(PROBE + "/body").with(realCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"가나다라마바사아자차카\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("nickname"));
    }

    @Test
    @WithMockUser
    void SEC12_없는_메서드는_405_METHOD_NOT_ALLOWED() throws Exception {
        mvc.perform(delete(ApiPaths.V1 + "/health").with(realCsrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @WithMockUser
    void SEC12_없는_경로는_404_NOT_FOUND() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void SEC12_예상_밖_오류는_500_고정_문구이고_내부_정보가_없다() throws Exception {
        mvc.perform(get(PROBE + "/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("잠시 뒤 다시 시도해 주세요"))
                .andExpect(content().string(not(containsString("select"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    @WithMockUser
    void 서비스_예외는_정해진_코드와_상태로_답한다() throws Exception {
        mvc.perform(get(PROBE + "/group-full"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_FULL"))
                .andExpect(jsonPath("$.fields").doesNotExist());
    }

    @Test
    void SEC07_응답에_CSP_헤더가_있다() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/health"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }

    /** 이 테스트에서만 쓰는 엔드포인트. 실제 앱에는 등록되지 않는다. */
    @RestController
    @RequestMapping(PROBE)
    static class ProbeController {

        record Body(@NotBlank @Size(max = 10) String nickname) {
        }

        @PostMapping("/body")
        Body body(@Valid @RequestBody Body body) {
            return body;
        }

        enum Color { RED, BLUE }

        record Choice(@NotNull Color color, Integer count) {
        }

        record Choices(java.util.List<Choice> items) {
        }

        @PostMapping("/choice")
        Choice choice(@Valid @RequestBody Choice choice) {
            return choice;
        }

        @PostMapping("/choices")
        Choices choices(@Valid @RequestBody Choices choices) {
            return choices;
        }

        @GetMapping("/choice")
        Color choiceParam(@RequestParam Color color) {
            return color;
        }

        @GetMapping("/count")
        int count(@RequestParam int n) {
            return n;
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("select * from users -- 내부 정보");
        }

        @GetMapping("/group-full")
        String groupFull() {
            throw new ApiException(ErrorCode.GROUP_FULL);
        }
    }
}
