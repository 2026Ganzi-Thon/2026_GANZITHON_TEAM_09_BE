
package com.haengunping.group.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.haengunping.group.GroupProgressSource;
import com.haengunping.group.domain.GroupType;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupDetailResponse(
        Long groupId,
        GroupType type,
        String name,
        MemberResponse me,
        List<MemberResponse> members,
        GroupProgressSource.TodayProgress today,
        GroupProgressSource.CharacterProgress character,
        boolean isDemo
) {}
