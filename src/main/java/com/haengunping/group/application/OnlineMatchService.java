package com.haengunping.group.application;
import com.haengunping.account.AccountQuery;
import com.haengunping.common.error.ApiFault;
import com.haengunping.group.GroupCreatedEvent;
import com.haengunping.group.MemberJoinedEvent;
import com.haengunping.group.api.dto.MatchResponse;
import com.haengunping.group.domain.*;
import com.haengunping.group.infra.*;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class OnlineMatchService {
    private final OnlineMatchMutex mutex;
    private final OnlineQueueRepository queue;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupService groupService;
    private final AccountQuery accounts;
    private final OnlineAliasGenerator names;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    public OnlineMatchService(OnlineMatchMutex mutex,OnlineQueueRepository queue,GroupRepository groups,
            GroupMemberRepository members,GroupService groupService,AccountQuery accounts,
            OnlineAliasGenerator names,ApplicationEventPublisher events,Clock clock) {
        this.mutex=mutex;this.queue=queue;this.groups=groups;this.members=members;
        this.groupService=groupService;this.accounts=accounts;this.names=names;this.events=events;this.clock=clock;
    }
    @Transactional
    public MatchResponse enter(long userId) {
        mutex.lock();
        accounts.lockForGroupChange(userId);
        if(!accounts.isAdultConfirmed(userId))throw new ApiFault(HttpStatus.FORBIDDEN,"ADULT_CONFIRMATION_REQUIRED","만 19세 이상 확인이 필요해요");
        if(queue.existsById(userId))throw ApiFault.conflict("ALREADY_QUEUED","이미 대기 중이에요");
        groupService.checkLimit(userId);
        var candidate=availableGroup();
        if(candidate!=null)return new MatchResponse("MATCHED",joinOnline(candidate,userId),null);
        queue.saveAndFlush(new OnlineQueueEntry(userId,clock.instant()));
        assignWaiting();
        // This user's queue row disappears when matched; group ID can then be discovered via membership.
        if(!queue.existsById(userId)) {
            var online=members.findByUserIdAndLeftAtIsNull(userId).stream()
                    .map(m->groups.findById(m.getGroupId()).orElse(null))
                    .filter(g->g!=null && g.getType()==GroupType.ONLINE)
                    .max(Comparator.comparing(Group::getId)).orElseThrow();
            return new MatchResponse("MATCHED",online.getId(),null);
        }
        return new MatchResponse("WAITING",null,queue.findById(userId).orElseThrow().getQueuedAt());
    }
    @Transactional
    public void cancel(long userId) {
        mutex.lock();accounts.lockForGroupChange(userId);queue.deleteById(userId);
    }
    @Transactional
    public void matchWaiting() {
        mutex.lock();assignWaiting();
    }
    /** Called with the global match lock in the same transaction. */
    private void assignWaiting() {
        List<OnlineQueueEntry> waiting=new ArrayList<>(queue.findAllByOrderByQueuedAtAsc());
        for(var entry:waiting) {
            if(!accounts.isActive(entry.getUserId())){queue.delete(entry);continue;}
            // The queued position occupies a slot in the 3-group limit.
            if(members.countByUserIdAndLeftAtIsNull(entry.getUserId())>=3){queue.delete(entry);continue;}
            Group target=availableGroup();
            if(target==null)break;
            accounts.lockForGroupChange(entry.getUserId());
            joinOnline(target,entry.getUserId());queue.delete(entry);
        }
        queue.flush();
        waiting=queue.findAllByOrderByQueuedAtAsc();
        while(waiting.size()>=4) {
            List<OnlineQueueEntry> batch=new ArrayList<>(waiting.subList(0,4));
            // Avoid a new group with stale/ineligible accounts.
            for(var entry:batch) accounts.lockForGroupChange(entry.getUserId());
            var group=groups.saveAndFlush(new Group(GroupType.ONLINE,names.groupName(),false,clock.instant()));
            events.publishEvent(new GroupCreatedEvent(group.getId()));
            for(var entry:batch) {joinOnline(group,entry.getUserId());queue.delete(entry);}
            queue.flush();waiting=queue.findAllByOrderByQueuedAtAsc();
        }
    }
    private Group availableGroup() {
        return groups.findByTypeAndArchivedAtIsNull(GroupType.ONLINE).stream()
            .filter(g->{long count=members.countByGroupIdAndLeftAtIsNull(g.getId());return count>0 && count<6;})
            .min(Comparator.comparingLong(g->members.countByGroupIdAndLeftAtIsNull(g.getId())))
            .orElse(null);
    }
    private long joinOnline(Group group,long userId) {
        // The online match mutex serializes matchers; lock the group row as well.
        Group locked=groups.lockById(group.getId()).orElseThrow(ApiFault::notFound);
        var current=members.findByGroupIdAndLeftAtIsNullOrderByJoinedAtAsc(locked.getId());
        if(current.size()>=6)throw ApiFault.conflict("GROUP_FULL","그룹 인원이 가득 찼어요");
        if(current.stream().anyMatch(m->m.getUserId()==userId))throw ApiFault.conflict("ALREADY_MEMBER","이미 참여 중이에요");
        var taken=new HashSet<String>();for(var m:current)taken.add(m.getDisplayName());
        var member=members.save(new GroupMember(locked.getId(),userId,names.next(taken),clock.instant()));
        events.publishEvent(new MemberJoinedEvent(locked.getId(),member.getId(),userId));
        return locked.getId();
    }
}
