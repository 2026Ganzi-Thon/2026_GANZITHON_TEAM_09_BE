package com.haengunping.character.application;

import com.haengunping.character.domain.GroupCharacter;
import com.haengunping.character.infra.GroupCharacterRepository;
import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import com.haengunping.group.GroupQuery;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 그룹 도감: 완성된 캐릭터, 완성 순(CHR-07). 구성원이 아니면 404. */
@Service
@Transactional(readOnly = true)
public class CollectionService {

    private final GroupCharacterRepository characters;
    private final GroupQuery groups;

    public CollectionService(GroupCharacterRepository characters, GroupQuery groups) {
        this.characters = characters;
        this.groups = groups;
    }

    public List<GroupCharacter> completed(long viewerId, long groupId) {
        if (groups.findActiveMember(groupId, viewerId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return characters.findByGroupIdAndCompletedOnIsNotNullOrderByCompletedOnAsc(groupId);
    }
}
