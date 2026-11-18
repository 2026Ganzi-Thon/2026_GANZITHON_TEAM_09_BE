package com.haengunping.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

public interface GroupProgressSource {

    Summary summary(long groupId, LocalDate today);

    Detail detail(long groupId, LocalDate today);

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TodayProgress(
            LocalDate date,
            int completed,
            int total
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CharacterProgress(
            Long characterId,
            String kind,
            int stage,
            double progress
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record JustCompleted(
            String kind,
            LocalDate completedOn
    ) {}

    record Summary(
            TodayProgress today,
            CharacterProgress character,
            JustCompleted justCompleted
    ) {}

    record Detail(
            TodayProgress today,
            CharacterProgress character
    ) {}
}
