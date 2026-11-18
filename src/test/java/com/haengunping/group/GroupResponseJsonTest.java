
package com.haengunping.group;

import com.haengunping.group.api.dto.*;
import com.haengunping.group.domain.GroupType;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class GroupResponseJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void GRP01_그룹목록_응답필드와_null_생략을_검증한다() throws Exception {
        var group = new GroupSummaryResponse(
                12L,
                GroupType.FRIEND,
                "우리 넷",
                2,
                new GroupProgressSource.TodayProgress(null, 1, 2),
                new GroupProgressSource.CharacterProgress(
                        null, "CLOVER", 2, 49.8
                ),
                false,
                null
        );

        var response = new GroupListResponse(
                List.of(group),
                new QueueInfoResponse(false, null)
        );

        JsonNode json = mapper.readTree(
                mapper.writeValueAsString(response)
        );

        JsonNode item = json.path("groups").get(0);

        assertEquals(12L, item.path("groupId").longValue());
        assertEquals(false, item.path("isDemo").booleanValue());

        assertFalse(item.has("id"));
        assertFalse(item.has("demo"));

        // 완성 연출 대상이 아닐 때는 필드 자체가 없어야 한다.
        assertFalse(item.has("justCompleted"));

        // 목록의 오늘 진행 정보에는 date가 없다.
        assertEquals(1, item.path("today").path("completed").intValue());
        assertFalse(item.path("today").has("date"));

        // 목록의 캐릭터 정보에는 characterId가 없다.
        assertEquals("CLOVER",
                item.path("character").path("kind").asText());
        assertFalse(item.path("character").has("characterId"));
    }

    @Test
    void GRP01_그룹상세_응답필드와_null_생략을_검증한다() throws Exception {
        var me = new MemberResponse(301L, "산책하는 수달");

        var response = new GroupDetailResponse(
                12L,
                GroupType.ONLINE,
                "아침 산책단",
                me,
                List.of(me),
                null,
                null,
                true
        );

        JsonNode json = mapper.readTree(
                mapper.writeValueAsString(response)
        );

        assertEquals(12L, json.path("groupId").longValue());
        assertEquals(301L, json.path("me")
                .path("memberId").longValue());
        assertTrue(json.path("isDemo").booleanValue());

        assertFalse(json.has("id"));
        assertFalse(json.has("self"));
        assertFalse(json.has("demo"));

        // 진행 정보가 없으면 null 대신 필드를 생략한다.
        assertFalse(json.has("today"));
        assertFalse(json.has("character"));
    }
}
