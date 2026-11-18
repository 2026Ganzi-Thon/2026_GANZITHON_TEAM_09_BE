package com.haengunping.ping.api;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** api.md 6절 핑. 그룹 구성원은 group_members에 직접 넣는다(어제부터 구성원). */
class PingControllerTest extends IntegrationTest {

    private static final String PINGS = ApiPaths.V1 + "/pings";

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;

    private long group;
    private long me;
    private long other;
    private long outsider;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        group = createGroup();
        me = createUser();
        other = createUser();
        outsider = createUser();
        join(me, "now() - interval '1 day'");
        join(other, "now() - interval '1 day'");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM blocks WHERE blocker_member_id IN (SELECT id FROM group_members WHERE group_id = ?)", group);
        jdbc.update("DELETE FROM group_members WHERE group_id = ?", group);
        deleteGroup(group);
        for (long user : new long[] {me, other, outsider}) {
            jdbc.update("DELETE FROM pings WHERE user_id = ?", user);
            jdbc.update("DELETE FROM daily_missions WHERE user_id = ?", user);
            deleteUser(user);
        }
    }

    @Test
    void PING02_미션을_완료하지_않았으면_409() throws Exception {
        mvc.perform(send(me, "CLOVER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MISSION_NOT_COMPLETED"));
    }

    @Test
    void PING02_완료하면_속한_그룹에_보내고_두_번째는_409() throws Exception {
        complete(me);

        mvc.perform(send(me, "CLOVER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupIds[0]").value(group));
        mvc.perform(send(me, "SUN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PINGED"));
    }

    @Test
    void PING01_모르는_이모티콘이면_400() throws Exception {
        complete(me);

        mvc.perform(send(me, "HEART"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("emoji"));
    }

    @Test
    void PING03_오늘_핑은_이모티콘만_주고_보낸_사람_시각은_없다() throws Exception {
        complete(other);
        mvc.perform(send(other, "SUN")).andExpect(status().isCreated());

        mvc.perform(get(ApiPaths.V1 + "/groups/" + group + "/pings").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.pings.length()").value(1))
                .andExpect(jsonPath("$.pings[0].emoji").value("SUN"))
                .andExpect(jsonPath("$.pings[0].userId").doesNotExist())
                .andExpect(jsonPath("$.pings[0].createdAt").doesNotExist());
    }

    @Test
    void GRP14_내가_차단한_구성원의_핑은_안_보인다() throws Exception {
        complete(other);
        mvc.perform(send(other, "SUN")).andExpect(status().isCreated());
        jdbc.update("""
                INSERT INTO blocks (blocker_member_id, blocked_member_id, created_at)
                SELECT a.id, b.id, now() FROM group_members a, group_members b
                WHERE a.group_id = ? AND a.user_id = ? AND b.group_id = a.group_id AND b.user_id = ?
                """, group, me, other);

        mvc.perform(get(ApiPaths.V1 + "/groups/" + group + "/pings").with(as(me)))
                .andExpect(jsonPath("$.pings.length()").value(0));
    }

    @Test
    void PING02_들어오기_전에_보낸_핑은_그_그룹에_안_간다() throws Exception {
        complete(outsider);
        mvc.perform(send(outsider, "CLAP")).andExpect(status().isCreated());
        join(outsider, "now()"); // 핑 다음에 들어옴, 지금은 구성원

        mvc.perform(get(ApiPaths.V1 + "/groups/" + group + "/pings").with(as(me)))
                .andExpect(jsonPath("$.pings.length()").value(0));
    }

    @Test
    void 구성원이_아니면_404() throws Exception {
        mvc.perform(get(ApiPaths.V1 + "/groups/" + group + "/pings").with(as(outsider)))
                .andExpect(status().isNotFound());
    }

    private void join(long user, String joinedAtSql) {
        jdbc.update("INSERT INTO group_members (group_id, user_id, display_name, joined_at) VALUES (?, ?, ?, " + joinedAtSql + ")",
                group, user, "u" + user);
    }

    private void complete(long user) {
        jdbc.update("INSERT INTO daily_missions (user_id, date, topic_id, completed_at) VALUES (?, ?, 1, now())", user, today);
    }

    private MockHttpServletRequestBuilder send(long user, String emoji) throws Exception {
        Cookie token = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return post(PINGS).with(as(user))
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"" + emoji + "\"}");
    }

    private static RequestPostProcessor as(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
