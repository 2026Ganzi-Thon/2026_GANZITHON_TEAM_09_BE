package com.haengunping.group.application;
import com.haengunping.account.AccountDeletedEvent;
import com.haengunping.account.AccountQuery;
import com.haengunping.account.NicknameChangedEvent;
import com.haengunping.common.error.ApiFault;
import com.haengunping.group.GroupCreatedEvent;
import com.haengunping.group.GroupProgressSource;
import com.haengunping.group.MemberJoinedEvent;
import com.haengunping.group.api.dto.*;
import com.haengunping.group.domain.*;
import com.haengunping.group.infra.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
@Service
public class GroupService {
    private static final ZoneId KST=ZoneId.of("Asia/Seoul");
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final OnlineQueueRepository queue;
    private final AccountQuery accounts;
    private final ObjectProvider<GroupProgressSource> progress;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    public GroupService(GroupRepository groups,GroupMemberRepository members,OnlineQueueRepository queue,
            AccountQuery accounts,ObjectProvider<GroupProgressSource> progress,Clock clock,ApplicationEventPublisher events) {
        this.groups=groups;this.members=members;this.queue=queue;this.accounts=accounts;
        this.progress=progress;this.clock=clock;this.events=events;
    }
    @Transactional
    public GroupDetailResponse create(long userId,CreateGroupRequest request) {
        accounts.lockForGroupChange(userId);
        checkLimit(userId);
        if(request.type()==null || request.type()==GroupType.ONLINE)throw ApiFault.validation("type은 FRIEND 또는 SOLO여야 해요");
        String name;
        if(request.type()==GroupType.FRIEND) {
            name=request.name();
            if(name==null || name.isBlank() || name.codePointCount(0,name.length())>12)throw ApiFault.validation("그룹 이름은 1~12자여야 해요");
        } else {name="나의 작은 공간";}
        var group=groups.saveAndFlush(new Group(request.type(),name,false,clock.instant()));
        var member=members.save(new GroupMember(group.getId(),userId,accounts.nicknameOf(userId),clock.instant()));
        events.publishEvent(new GroupCreatedEvent(group.getId()));
        events.publishEvent(new MemberJoinedEvent(group.getId(),member.getId(),userId));
        return detail(userId,group.getId());
    }
    @Transactional(readOnly=true)
    public GroupListResponse list(long userId) {
        List<GroupSummaryResponse> result=members.findByUserIdAndLeftAtIsNull(userId).stream()
                .map(m->groups.findById(m.getGroupId()).orElse(null))
                .filter(g->g!=null && g.getArchivedAt()==null)
                .map(g->summary(g)).toList();
        var entry=queue.findById(userId);
        return new GroupListResponse(result,new QueueInfoResponse(entry.isPresent(),entry.map(OnlineQueueEntry::getQueuedAt).orElse(null)));
    }
    @Transactional(readOnly=true)
    public GroupDetailResponse detail(long userId,long groupId) {
        Group group=groups.findById(groupId).filter(g->g.getArchivedAt()==null).orElseThrow(ApiFault::notFound);
        GroupMember self=members.findByGroupIdAndUserIdAndLeftAtIsNull(groupId,userId).orElseThrow(ApiFault::notFound);
        var active=members.findByGroupIdAndLeftAtIsNullOrderByJoinedAtAsc(groupId);
        var visible=active.stream().map(m->new MemberResponse(m.getId(),m.getDisplayName())).toList();
        Map<String,Object> data=progress.getIfAvailable()!=null?progress.getObject().detail(groupId,today()):Map.of();
        return new GroupDetailResponse(group.getId(),group.getType(),group.getName(),
                new MemberResponse(self.getId(),self.getDisplayName()),visible,
                (Map<String,Object>)data.get("today"),(Map<String,Object>)data.get("character"),group.isDemo());
    }
    @Transactional
    public void leave(long userId,long groupId) { leaveWithReason(userId,groupId,LeftReason.LEFT); }
    @Transactional
    public void leaveWithReason(long userId,long groupId,LeftReason reason) {
        Group group=groups.lockById(groupId).filter(g->g.getArchivedAt()==null).orElseThrow(ApiFault::notFound);
        var member=members.findByGroupIdAndUserIdAndLeftAtIsNull(groupId,userId).orElseThrow(ApiFault::notFound);
        member.leave(clock.instant(),reason);
        members.flush();
        if(members.countByGroupIdAndLeftAtIsNull(groupId)==0)group.archive(clock.instant());
    }
    @Transactional
    public void inactiveRemove(long groupId,long userId) { 
        if(members.findByGroupIdAndUserIdAndLeftAtIsNull(groupId,userId).isPresent())leaveWithReason(userId,groupId,LeftReason.INACTIVE);
    }
    @EventListener
    @Transactional
    public void accountDeleted(AccountDeletedEvent event) {
        queue.deleteById(event.userId());
        for(var membership:members.findByUserIdAndLeftAtIsNull(event.userId()))leaveWithReason(event.userId(),membership.getGroupId(),LeftReason.ACCOUNT_DELETED);
    }
    @EventListener
    @Transactional
    public void nicknameChanged(NicknameChangedEvent event) {
        for(var membership:members.findByUserIdAndLeftAtIsNull(event.userId())) {
            var group=groups.findById(membership.getGroupId()).orElse(null);
            if(group!=null && group.getType()!=GroupType.ONLINE) {
                boolean duplicated=members.findByGroupIdAndLeftAtIsNullOrderByJoinedAtAsc(group.getId()).stream()
                    .anyMatch(other->!other.getId().equals(membership.getId()) && other.getDisplayName().equals(event.newNickname()));
                if(duplicated)throw ApiFault.validation("참여 중인 친구 그룹에 같은 닉네임이 있어요");
                membership.setDisplayName(event.newNickname());
            }
        }
    }
    @Transactional(readOnly=true)
    public void checkLimit(long userId) {
        if(members.countByUserIdAndLeftAtIsNull(userId)+(queue.existsById(userId)?1:0)>=3)
            throw ApiFault.conflict("GROUP_LIMIT","그룹은 최대 3개까지 참여할 수 있어요");
    }
    private GroupSummaryResponse summary(Group g) {
        Map<String,Object> data=progress.getIfAvailable()!=null?progress.getObject().summary(g.getId(),today()):Map.of();
        return new GroupSummaryResponse(g.getId(),g.getType(),g.getName(),
                (int)members.countByGroupIdAndLeftAtIsNull(g.getId()),
                (Map<String,Object>)data.get("today"),(Map<String,Object>)data.get("character"),g.isDemo(),
                (Map<String,Object>)data.get("justCompleted"));
    }
    private LocalDate today() {return LocalDate.now(clock.withZone(KST));}
}
