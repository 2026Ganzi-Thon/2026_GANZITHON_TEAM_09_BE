package com.haengunping.group;

import com.haengunping.common.error.GlobalExceptionHandler;
import com.haengunping.group.api.GroupController;
import com.haengunping.group.api.dto.GroupDetailResponse;
import com.haengunping.group.api.dto.GroupListResponse;
import com.haengunping.group.api.dto.GroupSummaryResponse;
import com.haengunping.group.api.dto.MemberResponse;
import com.haengunping.group.api.dto.QueueInfoResponse;
import com.haengunping.group.application.GroupService;
import com.haengunping.group.domain.GroupType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupControllerTest {

    private static final long USER_ID = 42L;
    private GroupService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(GroupService.class);
        mvc = MockMvcBuilders.standaloneSetup(new GroupController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void GRP01_목록_API는_명세의_JSON_필드명으로_응답한다() throws Exception {
        var summary = new GroupSummaryResponse(
                12L, GroupType.FRIEND, "우리 팀", 2,
                null, null, false, null
        );
        when(service.list(USER_ID)).thenReturn(
                new GroupListResponse(List.of(summary), new QueueInfoResponse(false, null))
        );

        mvc.perform(get("/api/v1/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].groupId").value(12))
                .andExpect(jsonPath("$.groups[0].isDemo").value(false))
                .andExpect(jsonPath("$.groups[0].id").doesNotExist())
                .andExpect(jsonPath("$.groups[0].demo").doesNotExist())
                .andExpect(jsonPath("$.groups[0].justCompleted").doesNotExist())
                .andExpect(jsonPath("$.queue.waiting").value(false));

        verify(service).list(USER_ID);
    }

    @Test
    void GRP02_상세_API는_me와_구성원을_응답한다() throws Exception {
        var me = new MemberResponse(301L, "가벼운 걸음");
        when(service.detail(USER_ID, 12L)).thenReturn(
                new GroupDetailResponse(
                        12L, GroupType.ONLINE, "아침 산책단",
                        me, List.of(me), null, null, true
                )
        );

        mvc.perform(get("/api/v1/groups/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(12))
                .andExpect(jsonPath("$.me.memberId").value(301))
                .andExpect(jsonPath("$.members[0].displayName").value("가벼운 걸음"))
                .andExpect(jsonPath("$.isDemo").value(true))
                .andExpect(jsonPath("$.self").doesNotExist())
                .andExpect(jsonPath("$.today").doesNotExist())
                .andExpect(jsonPath("$.character").doesNotExist());

        verify(service).detail(USER_ID, 12L);
    }

    @Test
    void GRP01_생성_API는_201과_그룹_상세를_응답한다() throws Exception {
        var me = new MemberResponse(401L, "고요한 하루");
        when(service.create(eq(USER_ID), argThat(request ->
                request.type() == GroupType.FRIEND && "우리 팀".equals(request.name())
        ))).thenReturn(new GroupDetailResponse(
                21L, GroupType.FRIEND, "우리 팀",
                me, List.of(me), null, null, false
        ));

        mvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FRIEND\",\"name\":\"우리 팀\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(21))
                .andExpect(jsonPath("$.me.memberId").value(401))
                .andExpect(jsonPath("$.isDemo").value(false));

        verify(service).create(eq(USER_ID), argThat(request ->
                request.type() == GroupType.FRIEND && "우리 팀".equals(request.name())
        ));
    }

    @Test
    void SEC01_인증되지_않으면_그룹_목록을_조회할_수_없다() throws Exception {
        SecurityContextHolder.clearContext();

        mvc.perform(get("/api/v1/groups"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
