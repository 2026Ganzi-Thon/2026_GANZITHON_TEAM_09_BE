package com.haengunping.character.api;

import com.haengunping.character.application.CollectionService;
import com.haengunping.character.domain.CharacterKind;
import com.haengunping.common.security.CurrentUser;
import com.haengunping.common.web.ApiPaths;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** api.md 3절 그룹 도감(담당 B로 A와 합의). */
@RestController
public class CollectionController {

    private final CollectionService service;

    public CollectionController(CollectionService service) {
        this.service = service;
    }

    @GetMapping(ApiPaths.V1 + "/groups/{groupId}/collection")
    public CollectionResponse collection(@PathVariable long groupId) {
        return new CollectionResponse(service.completed(CurrentUser.id(), groupId).stream()
                .map(c -> new Item(c.id(), c.kind(), c.startedOn(), c.completedOn()))
                .toList());
    }

    public record CollectionResponse(List<Item> characters) {
    }

    public record Item(long characterId, CharacterKind kind, LocalDate startedOn, LocalDate completedOn) {
    }
}
