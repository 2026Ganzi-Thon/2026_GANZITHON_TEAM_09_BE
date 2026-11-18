package com.haengunping.group.api.dto;

import com.haengunping.group.GroupProgressSource;
import com.haengunping.group.domain.GroupType;
import java.util.List;

public record GroupDetailResponse(
        Long id,
        GroupType type,
        String name,
        MemberResponse self,
        List<MemberResponse> members,
        GroupProgressSource.TodayProgress today,
        GroupProgressSource.CharacterProgress character,
        boolean demo
) {}