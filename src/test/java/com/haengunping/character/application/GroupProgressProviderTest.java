package com.haengunping.character.application;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.account.application.AccountService;
import com.haengunping.common.web.ApiPaths;
import com.haengunping.support.IntegrationTest;
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

/** 그룹 목록·상세 응답의 today·character·justCompleted(CHR-05·06·10, api.md 3절). 구성원·캐릭터는 DB에 직접 넣는다. */
class GroupProgressProviderTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;
    @Autowired
    AccountService accounts;

    private LocalDate today;
    private long group;
    private long me;
    private long other;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        group = createGroup();
        me = createUser();
        other = createUser();
        join(me);
        join(other);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM daily_group_progress WHERE group_id = ?", group);
        jdbc.update("DELETE FROM characters WHERE group_id = ?", group);
        jdbc.update("DELETE FROM group_members WHERE group_id = ?", group);
        deleteGroup(group);
        for (long user : new long[] {me, other}) {
            jdbc.update("DELETE FROM daily_missions WHERE user_id = ?", user);
            deleteUser(user);
        }
    }

    @Test
    void CHR05_상세는_오늘_N명_중_M명과_확정_더하기_오늘_임시_성장을_준다() throws Exception {
        character("CLOVER", today.minusDays(1), null);
        confirmed(today.minusDays(1), 2, 2); // 확정 2
        completed(me); // 오늘 2명 중 1명 → 임시 1

        as(me, get(ApiPaths.V1 + "/groups/" + group))
                .andExpect(jsonPath("$.today.date").value(today.toString()))
                .andExpect(jsonPath("$.today.completed").value(1))
                .andExpect(jsonPath("$.today.total").value(2))
                .andExpect(jsonPath("$.character.characterId").isNumber())
                .andExpect(jsonPath("$.character.kind").value("CLOVER"))
                .andExpect(jsonPath("$.character.stage").value(1))
                .andExpect(jsonPath("$.character.progress").value(3.0))
                .andExpect(jsonPath("$.justCompleted").doesNotExist());
    }

    @Test
    void CHR10_목록에는_date_구성원별_완료가_없고_characterId는_있다() throws Exception {
        character("SEED", today, null);
        completed(me);
        long characterId = jdbc.queryForObject(
                "SELECT id FROM characters WHERE group_id = ? AND completed_on IS NULL", Long.class, group);

        // characterId: 프론트가 '나만 보는 캐릭터 별명'을 그룹+캐릭터 ID로 기기에 저장한다(2026-11-19 추가)
        as(me, get(ApiPaths.V1 + "/groups"))
                .andExpect(jsonPath("$.groups[0].today.completed").value(1))
                .andExpect(jsonPath("$.groups[0].today.date").doesNotExist())
                .andExpect(jsonPath("$.groups[0].character.kind").value("SEED"))
                .andExpect(jsonPath("$.groups[0].character.characterId").value(characterId))
                .andExpect(jsonPath("$.groups[0].justCompleted").doesNotExist());
        // 상세의 구성원은 memberId·displayName뿐
        as(me, get(ApiPaths.V1 + "/groups/" + group))
                .andExpect(jsonPath("$.members[0].length()").value(2))
                .andExpect(jsonPath("$.me.length()").value(2));
    }

    @Test
    void CHR07_어제_완성된_캐릭터는_오늘_목록에_justCompleted로_준다() throws Exception {
        character("SPROUT", today.minusDays(30), today.minusDays(1));
        character("SEED", today, null);

        as(me, get(ApiPaths.V1 + "/groups"))
                .andExpect(jsonPath("$.groups[0].justCompleted.kind").value("SPROUT"))
                .andExpect(jsonPath("$.groups[0].justCompleted.completedOn").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.groups[0].character.kind").value("SEED"));
    }

    @Test
    void CHR07_그저께_완성된_캐릭터는_justCompleted가_없다() throws Exception {
        character("SPROUT", today.minusDays(30), today.minusDays(2));
        character("SEED", today.minusDays(1), null);

        as(me, get(ApiPaths.V1 + "/groups"))
                .andExpect(jsonPath("$.groups[0].justCompleted").doesNotExist());
    }

    @Test
    void ACC08_완료한_사람이_계정을_지우면_오늘_임시_성장은_줄고_확정은_그대로() throws Exception {
        character("CLOVER", today.minusDays(1), null);
        confirmed(today.minusDays(1), 2, 2);
        completed(me);
        completed(other);
        as(me, get(ApiPaths.V1 + "/groups/" + group))
                .andExpect(jsonPath("$.character.progress").value(4.0)); // 확정 2 + 오늘 2

        accounts.delete(other);

        as(me, get(ApiPaths.V1 + "/groups/" + group))
                .andExpect(jsonPath("$.today.completed").value(1))
                .andExpect(jsonPath("$.today.total").value(1))
                .andExpect(jsonPath("$.character.progress").value(4.0)); // 1명 중 1명 → 임시 2, 확정 2 그대로

        jdbc.update("DELETE FROM daily_missions WHERE user_id = ?", me);
        as(me, get(ApiPaths.V1 + "/groups/" + group))
                .andExpect(jsonPath("$.character.progress").value(2.0)); // 오늘 완료가 없어도 확정 2는 줄지 않는다(CHR-08)
    }

    private void join(long user) {
        jdbc.update("INSERT INTO group_members (group_id, user_id, display_name, joined_at) VALUES (?, ?, ?, now() - interval '1 day')",
                group, user, "u" + user);
    }

    private void character(String kind, LocalDate startedOn, LocalDate completedOn) {
        jdbc.update("INSERT INTO characters (group_id, kind, started_on, completed_on) VALUES (?, ?, ?, ?)",
                group, kind, startedOn, completedOn);
    }

    private void confirmed(LocalDate day, int members, int completed) {
        jdbc.update("INSERT INTO daily_group_progress (group_id, date, member_count, completed_count, finalized_at) VALUES (?, ?, ?, ?, now())",
                group, day, members, completed);
    }

    private void completed(long user) {
        jdbc.update("INSERT INTO daily_missions (user_id, date, topic_id, completed_at) VALUES (?, ?, 1, now())", user, today);
    }

    private ResultActions as(long user, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(authentication(new UsernamePasswordAuthenticationToken(user, null, List.of()))))
                .andExpect(status().isOk());
    }
}
