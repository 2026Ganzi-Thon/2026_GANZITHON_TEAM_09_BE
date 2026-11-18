package com.haengunping.group;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
/** B·C 모듈에 공개하는 유일한 그룹 조회 진입점. */
public interface GroupQuery {
    Optional<MemberRef> findActiveMember(long groupId,long userId);
    List<Long> activeGroupIdsOf(long userId);
    List<Long> activeUserIdsOf(long groupId,Instant at);
    boolean isBlocked(long groupId,long viewerUserId,long authorUserId);
}
