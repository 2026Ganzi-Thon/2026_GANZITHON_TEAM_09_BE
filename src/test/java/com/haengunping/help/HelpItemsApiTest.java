package com.haengunping.help;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 도움받을 곳 목록 API(HELP-01·02·05·07). 실제 PostgreSQL과 시드 데이터로 확인한다. */
class HelpItemsApiTest extends IntegrationTest {

    private static final String ITEMS = ApiPaths.V1 + "/help/items";

    @Test
    void HELP01_쿠키_없이도_목록을_준다() throws Exception {
        mvc.perform(get(ITEMS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty());
    }

    @Test
    void HELP07_24시간_채널이_맨_앞이고_구역_순서대로_온다() throws Exception {
        String body = mvc.perform(get(ITEMS)).andReturn().getResponse().getContentAsString();

        List<String> sections = com.jayway.jsonpath.JsonPath.read(body, "$.items[*].section");
        List<String> ids = com.jayway.jsonpath.JsonPath.read(body, "$.items[*].id");

        assertThat(ids.subList(0, 4)).containsExactly("line-109", "line-15770199", "line-1388", "line-120");
        assertThat(sections).isSortedAccordingTo((a, b) -> order(a) - order(b));
    }

    @Test
    void HELP02_서울_25개_구_센터가_있고_구를_고른_사람에게만_보이는_조건이_붙는다() throws Exception {
        mvc.perform(get(ITEMS))
                .andExpect(jsonPath("$.items[?(@.kind == 'LOCAL_CENTER')]", hasSize(25)))
                .andExpect(jsonPath("$.items[?(@.id == 'center-마포구')].rules.sido[0]").value("서울특별시"))
                .andExpect(jsonPath("$.items[?(@.id == 'center-마포구')].rules.sigungu[0]").value("마포구"))
                .andExpect(jsonPath("$.items[?(@.id == 'mental-center')].kind").value("CENTER_FINDER"));
    }

    @Test
    void HELP02_규칙은_정한_키만_있고_비어_있으면_항상_보인다() throws Exception {
        mvc.perform(get(ITEMS))
                .andExpect(jsonPath("$.items[?(@.id == 'line-109')].rules").value(org.hamcrest.Matchers.contains(java.util.Map.of())))
                .andExpect(jsonPath("$.items[?(@.id == 'seoul-youth-mind')].rules.minAge").value(19))
                .andExpect(jsonPath("$.items[?(@.id == 'seoul-youth-mind')].rules.maxAge").value(39))
                .andExpect(jsonPath("$.items[?(@.id == 'job-mind')].rules.jobs[0]").value("JOB_SEEKER"));
    }

    @Test
    void 바우처는_의뢰서가_필요하다는_안내를_함께_준다() throws Exception {
        mvc.perform(get(ITEMS))
                .andExpect(jsonPath("$.items[?(@.id == 'voucher')].note",
                        org.hamcrest.Matchers.contains(startsWith("정신건강복지센터나 학교 상담센터에서 의뢰서"))));
    }

    @Test
    void HELP05_ETag가_있고_바뀌지_않았으면_304로_답한다() throws Exception {
        String etag = mvc.perform(get(ITEMS))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, not(org.hamcrest.Matchers.containsString("no-store"))))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mvc.perform(get(ITEMS).header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void 응답에_기준일_필드가_있다() throws Exception {
        mvc.perform(get(ITEMS))
                .andExpect(jsonPath("$.version").isString())
                .andExpect(jsonPath("$.syncedAt").hasJsonPath());
    }

    private static int order(String section) {
        return switch (section) {
            case "CRISIS_24H" -> 0;
            case "COUNSELING" -> 1;
            case "DROP_IN" -> 2;
            default -> throw new IllegalArgumentException(section);
        };
    }
}
