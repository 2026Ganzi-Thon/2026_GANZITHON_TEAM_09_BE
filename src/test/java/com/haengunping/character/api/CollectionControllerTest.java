package com.haengunping.character.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** api.md 3절 GET /groups/{groupId}/collection. */
class CollectionControllerTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private long group;
    private long me;
    private long outsider;

    @BeforeEach
    void setUp() {
        group = createGroup();
        me = createUser();
        outsider = createUser();
        jdbc.update("INSERT INTO group_members (group_id, user_id, display_name, joined_at) VALUES (?, ?, 'me', now())", group, me);
        // 완성 2개(늦게 완성한 것을 먼저 넣음) + 진행 중 1개
        jdbc.update("INSERT INTO characters (group_id, kind, started_on, completed_on) VALUES (?, 'CLOVER', DATE '2026-10-01', DATE '2026-10-30')", group);
        jdbc.update("INSERT INTO characters (group_id, kind, started_on, completed_on) VALUES (?, 'SEED', DATE '2026-09-01', DATE '2026-09-30')", group);
        jdbc.update("INSERT INTO characters (group_id, kind, started_on) VALUES (?, 'SPROUT', DATE '2026-10-31')", group);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM characters WHERE group_id = ?", group);
        jdbc.update("DELETE FROM group_members WHERE group_id = ?", group);
        deleteGroup(group);
        deleteUser(me);
        deleteUser(outsider);
    }

    @Test
    void CHR07_도감은_완성된_캐릭터만_완성_순으로_준다() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/groups/" + group + "/collection").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characters.length()").value(2))
                .andExpect(jsonPath("$.characters[0].kind").value("SEED"))
                .andExpect(jsonPath("$.characters[0].characterId").isNumber())
                .andExpect(jsonPath("$.characters[0].startedOn").value("2026-09-01"))
                .andExpect(jsonPath("$.characters[0].completedOn").value("2026-09-30"))
                .andExpect(jsonPath("$.characters[1].kind").value("CLOVER"));
    }

    @Test
    void 구성원이_아니면_404() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/groups/" + group + "/collection").with(as(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void SEC01_로그인하지_않으면_401() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/groups/" + group + "/collection")).andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor as(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
