
package com.haengunping.group.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.haengunping.group.GroupProgressSource;
import com.haengunping.group.domain.GroupType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupSummaryResponse(
        Long groupId,
        GroupType type,
        String name,
        int memberCount,
        GroupProgressSource.TodayProgress today,
        GroupProgressSource.CharacterProgress character,
        boolean isDemo,
        GroupProgressSource.JustCompleted justCompleted
) {}
