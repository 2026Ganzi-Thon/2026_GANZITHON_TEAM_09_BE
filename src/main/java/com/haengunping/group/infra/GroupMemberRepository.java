package com.haengunping.group.infra;
import com.haengunping.group.domain.GroupMember;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface GroupMemberRepository extends JpaRepository<GroupMember,Long> {
    long countByUserIdAndLeftAtIsNull(long userId);
    long countByGroupIdAndLeftAtIsNull(long groupId);
    boolean existsByGroupIdAndDisplayNameAndLeftAtIsNull(long groupId,String displayName);
    List<GroupMember> findByUserIdAndLeftAtIsNull(long userId);
    List<GroupMember> findByGroupIdAndLeftAtIsNullOrderByJoinedAtAsc(long groupId);
    Optional<GroupMember> findByGroupIdAndUserIdAndLeftAtIsNull(long groupId,long userId);
    Optional<GroupMember> findByIdAndGroupIdAndLeftAtIsNull(long id,long groupId);
    @Query("select gm.userId from GroupMember gm where gm.groupId=:groupId and gm.joinedAt <= :at and (gm.leftAt is null or gm.leftAt > :at)")
    List<Long> userIdsActiveAt(@Param("groupId")long groupId,@Param("at")Instant at);
    @Query("select gm from GroupMember gm where gm.leftAt is null and gm.groupId in (select g.id from Group g where g.type = com.haengunping.group.domain.GroupType.ONLINE and g.archivedAt is null)")
    List<GroupMember> activeOnlineMembers();
}
