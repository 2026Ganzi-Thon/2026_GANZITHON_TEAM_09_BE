package com.haengunping.mission.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.ResultActions;

/** MIS-08: 다 했어요 응답의 그룹별 오늘 완료 수·캐릭터 변화. 구성원·캐릭터는 DB에 직접 넣는다. */
class CompleteGroupsTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;

    private LocalDate today;
    private long group;
    private long me;
    private long other;
    private long latecomer;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        group = createGroup();
        me = createUser();
        other = createUser();
        latecomer = createUser();
        join(me);
        join(other);
        jdbc.update("INSERT INTO characters (group_id, kind, started_on) VALUES (?, 'SEED', ?)", group, today.minusDays(1));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM daily_group_progress WHERE group_id = ?", group);
        jdbc.update("DELETE FROM characters WHERE group_id = ?", group);
        jdbc.update("DELETE FROM group_members WHERE group_id = ?", group);
        deleteGroup(group);
        for (long user : new long[] {me, other, latecomer}) {
            jdbc.update("DELETE FROM daily_missions WHERE user_id = ?", user);
            deleteUser(user);
        }
    }

    @Test
    void MIS08_내가_속한_그룹마다_오늘_N명_중_M명과_캐릭터를_주고_구성원별_완료는_없다() throws Exception {
        completedEarlier(other);

        complete(me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].groupId").value(group))
                .andExpect(jsonPath("$.groups[0].today.completed").value(2))
                .andExpect(jsonPath("$.groups[0].today.total").value(2))
                .andExpect(jsonPath("$.groups[0].character.stage").value(1))
                .andExpect(jsonPath("$.groups[0].character.progress").value(2.0)) // 확정 0 + 오늘 2×2÷2
                .andExpect(jsonPath("$.groups[0].character.stageChanged").value(false))
                .andExpect(jsonPath("$.groups[0].members").doesNotExist())
                .andExpect(jsonPath("$.groups[0].completedUserIds").doesNotExist());
    }

    @Test
    void MIS08_내_완료로_단계가_바뀌면_stageChanged() throws Exception {
        // 어제까지 확정 24 = 1인 하루 2 × 12일
        for (int i = 1; i <= 12; i++) {
            jdbc.update("INSERT INTO daily_group_progress (group_id, date, member_count, completed_count, finalized_at) VALUES (?, ?, 1, 1, now())",
                    group, today.minusDays(1).minusDays(12 - i));
        }
        jdbc.update("UPDATE characters SET started_on = ? WHERE group_id = ?", today.minusDays(12), group);

        complete(me) // 24 + 2×1÷2 = 25 → 2단계
                .andExpect(jsonPath("$.groups[0].character.progress").value(25.0))
                .andExpect(jsonPath("$.groups[0].character.stage").value(2))
                .andExpect(jsonPath("$.groups[0].character.stageChanged").value(true));
    }

    @Test
    void MIS08_완료한_뒤_그룹에_들어온_사람도_오늘_완료로_센다() throws Exception {
        completedEarlier(latecomer);
        join(latecomer);

        complete(me)
                .andExpect(jsonPath("$.groups[0].today.completed").value(2))
                .andExpect(jsonPath("$.groups[0].today.total").value(3));
    }

    private void join(long user) {
        jdbc.update("INSERT INTO group_members (group_id, user_id, display_name, joined_at) VALUES (?, ?, ?, now() - interval '1 minute')",
                group, user, "u" + user);
    }

    private void completedEarlier(long user) {
        jdbc.update("INSERT INTO daily_missions (user_id, date, topic_id, completed_at) VALUES (?, ?, 1, now())", user, today);
    }

    private ResultActions complete(long user) throws Exception {
        Cookie token = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return mvc.perform(post(ApiPaths.V1 + "/missions/today/complete")
                .with(authentication(new UsernamePasswordAuthenticationToken(user, null, List.of())))
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue()));
    }
}
