package com.haengunping.group.application;
import com.haengunping.common.error.ApiFault;
import com.haengunping.common.ratelimit.FixedWindowRateLimiter;
import com.haengunping.group.domain.*;
import com.haengunping.group.infra.*;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class GroupModerationService {
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final BlockRepository blocks;
    private final ReportRepository reports;
    private final GroupService groupService;
    private final FixedWindowRateLimiter limiter;
    private final Clock clock;
    public GroupModerationService(GroupRepository groups,GroupMemberRepository members,BlockRepository blocks,
            ReportRepository reports,GroupService groupService,FixedWindowRateLimiter limiter,Clock clock) {
        this.groups=groups;this.members=members;this.blocks=blocks;this.reports=reports;
        this.groupService=groupService;this.limiter=limiter;this.clock=clock;
    }
    private GroupMember self(long groupId,long userId) {
        return members.findByGroupIdAndUserIdAndLeftAtIsNull(groupId,userId).orElseThrow(ApiFault::notFound);
    }
    private GroupMember target(long groupId,long memberId) {
        return members.findByIdAndGroupIdAndLeftAtIsNull(memberId,groupId).orElseThrow(ApiFault::notFound);
    }
    private void online(long groupId) {
        Group group=groups.lockById(groupId).filter(g->g.getArchivedAt()==null).orElseThrow(ApiFault::notFound);
        if(group.getType()!=GroupType.ONLINE)throw ApiFault.conflict("NOT_ALLOWED_FOR_GROUP_TYPE","온라인 그룹에서만 사용할 수 있어요");
    }
    @Transactional
    public void block(long userId,long groupId,long targetId) {
        GroupMember myself=self(groupId,userId);online(groupId);GroupMember other=target(groupId,targetId);
        if(myself.getId().equals(other.getId()))throw ApiFault.validation("자기 자신을 차단할 수 없어요");
        BlockId key=new BlockId(myself.getId(),other.getId());
        if(!blocks.existsById(key))blocks.save(new Block(key,clock.instant()));
    }
    @Transactional
    public void unblock(long userId,long groupId,long targetId) {
        GroupMember myself=self(groupId,userId);online(groupId);GroupMember other=target(groupId,targetId);
        blocks.deleteById(new BlockId(myself.getId(),other.getId()));
    }
    @Transactional
    public void report(long userId,long groupId,long targetId,ReportReason reason) {
        GroupMember myself=self(groupId,userId);online(groupId);GroupMember other=target(groupId,targetId);
        if(reason==null)throw ApiFault.validation("신고 사유가 필요해요");
        if(myself.getId().equals(other.getId()))throw ApiFault.validation("자기 자신을 신고할 수 없어요");
        var today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        limiter.check("report:"+userId+":"+today,10,Duration.ofDays(1));
        if(reports.existsByReporterMemberIdAndTargetMemberId(myself.getId(),other.getId()))return;
        reports.saveAndFlush(new Report(groupId,myself.getId(),other.getId(),reason,clock.instant()));
        if(reports.countByGroupIdAndTargetMemberId(groupId,other.getId())>=2)
            groupService.leaveWithReason(other.getUserId(),groupId,LeftReason.REPORTED);
    }
}
