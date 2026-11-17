package com.haengunping.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.notification.infra.PushSubscriptionRepository;
import com.haengunping.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
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

/** api.md 9절 요청·응답 모양과 인증·검증을 실제 DB로 확인한다. 로그인은 A의 세션 필터 대신 SecurityContext에 사용자 id를 넣는다. */
class NotificationControllerTest extends IntegrationTest {

    private static final String SUBSCRIPTIONS = ApiPaths.V1 + "/me/push-subscriptions";
    private static final String SETTINGS = ApiPaths.V1 + "/me/notification-settings";

    @Autowired
    PushSubscriptionRepository subscriptions;
    @Autowired
    JdbcTemplate jdbc;

    private long me;
    private String endpoint;

    @BeforeEach
    void createMe() {
        me = createUser();
        endpoint = "https://fcm.googleapis.com/fcm/send/" + me;
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ?", me);
        jdbc.update("DELETE FROM notification_settings WHERE user_id = ?", me);
        deleteUser(me);
    }

    @Test
    void NOTI01_구독하면_201_해지하면_204() throws Exception {
        mvc.perform(json(post(SUBSCRIPTIONS), """
                        {"endpoint":"%s","keys":{"p256dh":"p1","auth":"a1"}}""".formatted(endpoint)))
                .andExpect(status().isCreated());
        assertThat(subscriptions.findByUserId(me)).hasSize(1);

        mvc.perform(json(delete(SUBSCRIPTIONS), """
                        {"endpoint":"%s"}""".formatted(endpoint)))
                .andExpect(status().isNoContent());
        assertThat(subscriptions.findByUserId(me)).isEmpty();
    }

    @Test
    void NOTI01_keys가_없으면_400() throws Exception {
        mvc.perform(json(post(SUBSCRIPTIONS), """
                        {"endpoint":"%s"}""".formatted(endpoint)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void NOTI02_설정이_없으면_기본값_저장하면_HH_mm으로_돌려준다() throws Exception {
        mvc.perform(get(SETTINGS).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionReminderTime").isEmpty())
                .andExpect(jsonPath("$.weeklyCheckReminder").value(true));

        mvc.perform(json(put(SETTINGS), """
                        {"missionReminderTime":"12:30","weeklyCheckReminder":false}"""))
                .andExpect(status().isOk());

        mvc.perform(get(SETTINGS).with(as(me)))
                .andExpect(jsonPath("$.missionReminderTime").value("12:30"))
                .andExpect(jsonPath("$.weeklyCheckReminder").value(false));
    }

    @Test
    void NOTI02_30분_단위가_아니면_400() throws Exception {
        mvc.perform(json(put(SETTINGS), """
                        {"missionReminderTime":"12:15","weeklyCheckReminder":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void NOTI02_null이면_미션_알림_끔() throws Exception {
        mvc.perform(json(put(SETTINGS), """
                        {"missionReminderTime":null,"weeklyCheckReminder":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionReminderTime").isEmpty());
    }

    @Test
    void SEC01_로그인하지_않으면_401() throws Exception {
        mvc.perform(get(SETTINGS)).andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor as(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    /** 로그인·실제 CSRF 흐름(ErrorResponseTest와 같은 방식)·JSON 본문을 붙인다. */
    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) throws Exception {
        Cookie token = mvc.perform(get(ApiPaths.V1 + "/health")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.with(as(me))
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
