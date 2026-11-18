package com.haengunping.group.api.dto;

import com.haengunping.group.GroupProgressSource;
import com.haengunping.group.domain.GroupType;

public record GroupSummaryResponse(
        Long id,
        GroupType type,
        String name,
        int memberCount,
        GroupProgressSource.TodayProgress today,
        GroupProgressSource.CharacterProgress character,
        boolean demo,
        GroupProgressSource.JustCompleted justCompleted
) {}