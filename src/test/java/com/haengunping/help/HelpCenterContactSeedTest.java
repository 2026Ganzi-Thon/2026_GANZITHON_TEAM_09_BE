package com.haengunping.help;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 서울 25개 구 정신건강복지센터의 주소·전화 시드(#56, HELP-02·03)가 25곳 모두 한 번씩 들어갔는지 본다. */
class HelpCenterContactSeedTest extends IntegrationTest {

    private static final String PHONE_FORMAT = "^02-\\d{3,4}-\\d{4}$";

    @Autowired
    private JdbcClient jdbc;

    @Test
    void 구별_센터_25곳_모두_전화와_주소가_채워졌다() {
        List<Map<String, Object>> rows = jdbc.sql(
                        "SELECT id, phone, address FROM help_items WHERE kind = 'LOCAL_CENTER' ORDER BY id")
                .query()
                .listOfRows();

        assertThat(rows).hasSize(25);
        assertThat(rows).allSatisfy(row -> {
            assertThat((String) row.get("phone")).as("%s 전화", row.get("id")).matches(PHONE_FORMAT);
            assertThat((String) row.get("address")).as("%s 주소", row.get("id")).startsWith("서울특별시 ");
        });
    }

    @Test
    void 주소에_그_구_이름이_들어_있고_센터_전화가_서로_겹치지_않는다() {
        List<Map<String, Object>> rows = jdbc.sql(
                        "SELECT id, phone, address, rules->'sigungu'->>0 AS gu FROM help_items WHERE kind = 'LOCAL_CENTER'")
                .query()
                .listOfRows();

        assertThat(rows).allSatisfy(row ->
                assertThat((String) row.get("address")).as("%s", row.get("id")).contains((String) row.get("gu")));
        assertThat(rows.stream().map(r -> r.get("phone")).distinct()).hasSize(25);
    }

    @Test
    void 사람이_다시_확인한_강북구_서대문구_전화로_고쳐져_있다() {
        assertThat(phoneOf("center-강북구")).isEqualTo("02-901-4860");
        assertThat(phoneOf("center-서대문구")).isEqualTo("02-337-2176");
    }

    private String phoneOf(String id) {
        return jdbc.sql("SELECT phone FROM help_items WHERE id = ?").param(id).query(String.class).single();
    }

    @Test
    void 도움받기_API가_센터의_전화와_주소를_그대로_준다() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/help/items"))
                .andExpect(jsonPath("$.items[?(@.id == 'center-마포구')].phone").value("02-3272-4937"))
                .andExpect(jsonPath("$.items[?(@.id == 'center-마포구')].address")
                        .value("서울특별시 마포구 성산로4길 15 성산1동주민센터 3층"));
    }
}
