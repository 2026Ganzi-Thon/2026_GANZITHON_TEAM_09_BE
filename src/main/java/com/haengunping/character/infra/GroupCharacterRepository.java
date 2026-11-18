package com.haengunping.character.infra;

import com.haengunping.character.domain.GroupCharacter;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GroupCharacterRepository extends JpaRepository<GroupCharacter, Long> {

    Optional<GroupCharacter> findByGroupIdAndCompletedOnIsNull(long groupId);

    /** 그날 확정할 캐릭터: 진행 중이고 그날 이전에 시작한 것. */
    List<GroupCharacter> findByCompletedOnIsNullAndStartedOnLessThanEqual(LocalDate day);

    /** 확정 처리를 시작할 날짜. 캐릭터가 없으면 null. */
    @Query("SELECT min(c.startedOn) FROM GroupCharacter c")
    LocalDate earliestStart();

    /** 그날 완성된 캐릭터(완성 연출 justCompleted). */
    Optional<GroupCharacter> findFirstByGroupIdAndCompletedOn(long groupId, LocalDate completedOn);

    /** 도감: 완성된 캐릭터, 완성 순. */
    List<GroupCharacter> findByGroupIdAndCompletedOnIsNotNullOrderByCompletedOnAsc(long groupId);
}
