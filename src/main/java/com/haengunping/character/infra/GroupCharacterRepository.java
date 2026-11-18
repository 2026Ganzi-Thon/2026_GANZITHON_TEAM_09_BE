package com.haengunping.character.infra;

import com.haengunping.character.domain.GroupCharacter;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupCharacterRepository extends JpaRepository<GroupCharacter, Long> {

    Optional<GroupCharacter> findByGroupIdAndCompletedOnIsNull(long groupId);

    /** 도감: 완성된 캐릭터, 완성 순. */
    List<GroupCharacter> findByGroupIdAndCompletedOnIsNotNullOrderByCompletedOnAsc(long groupId);
}
