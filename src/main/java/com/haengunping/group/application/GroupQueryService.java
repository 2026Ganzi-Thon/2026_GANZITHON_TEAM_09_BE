package com.haengunping.group.application;
import com.haengunping.group.GroupQuery;
import com.haengunping.group.MemberRef;
import com.haengunping.group.domain.BlockId;
import com.haengunping.group.infra.BlockRepository;
import com.haengunping.group.infra.GroupMemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class GroupQueryService implements GroupQuery {
    private final GroupMemberRepository members;
    private final BlockRepository blocks;
    public GroupQueryService(GroupMemberRepository members,BlockRepository blocks){this.members=members;this.blocks=blocks;}
    @Override public Optional<MemberRef> findActiveMember(long groupId,long userId) {
        return members.findByGroupIdAndUserIdAndLeftAtIsNull(groupId,userId)
                .map(m->new MemberRef(m.getId(),m.getGroupId(),m.getDisplayName()));
    }
    @Override public List<Long> activeGroupIdsOf(long userId) {
        return members.findByUserIdAndLeftAtIsNull(userId).stream().map(m->m.getGroupId()).distinct().toList();
    }
    @Override public List<Long> activeUserIdsOf(long groupId,Instant at) { return members.userIdsActiveAt(groupId,at); }
    @Override public boolean isBlocked(long groupId,long viewerUserId,long authorUserId) {
        var viewer=members.findByGroupIdAndUserIdAndLeftAtIsNull(groupId,viewerUserId);
        var author=members.findByGroupIdAndUserIdAndLeftAtIsNull(groupId,authorUserId);
        return viewer.isPresent() && author.isPresent()
                && blocks.existsById(new BlockId(viewer.get().getId(),author.get().getId()));
    }
}
