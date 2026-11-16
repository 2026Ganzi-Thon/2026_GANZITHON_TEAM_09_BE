package com.haengunping.group.application;
import com.haengunping.account.AccountQuery;
import com.haengunping.common.error.ApiFault;
import com.haengunping.group.MemberJoinedEvent;
import com.haengunping.group.api.dto.GroupDetailResponse;
import com.haengunping.group.api.dto.InviteResponse;
import com.haengunping.group.domain.*;
import com.haengunping.group.infra.*;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class InviteService {
    private static final String ALPHABET="23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private final SecureRandom random=new SecureRandom();
    private final InviteRepository invites;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final AccountQuery accounts;
    private final GroupService groupService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    public InviteService(InviteRepository invites,GroupRepository groups,GroupMemberRepository members,
            AccountQuery accounts,GroupService groupService,ApplicationEventPublisher events,Clock clock) {
        this.invites=invites;this.groups=groups;this.members=members;this.accounts=accounts;
        this.groupService=groupService;this.events=events;this.clock=clock;
    }
    @Transactional
    public InviteResponse create(long userId,long groupId) {
        Group group=groups.lockById(groupId).filter(g->g.getArchivedAt()==null).orElseThrow(ApiFault::notFound);
        GroupMember inviter=members.findByGroupIdAndUserIdAndLeftAtIsNull(groupId,userId).orElseThrow(ApiFault::notFound);
        if(group.getType()!=GroupType.FRIEND)throw ApiFault.conflict("NOT_ALLOWED_FOR_GROUP_TYPE","친구 그룹만 초대할 수 있어요");
        Instant now=clock.instant();
        for(var code:invites.findByGroupIdAndRevokedAtIsNull(groupId))code.revoke(now);
        String code=null;
        for(int tries=0;tries<25;tries++) {
            String candidate=newCode();
            if(!invites.existsByCode(candidate)) {code=candidate;break;}
        }
        if(code==null)throw new IllegalStateException("초대 코드 생성 실패");
        var saved=invites.save(new Invite(groupId,code,inviter.getId(),now.plus(Duration.ofDays(7))));
        return new InviteResponse(saved.getCode(),saved.getExpiresAt());
    }
    @Transactional
    public GroupDetailResponse join(long userId,String rawCode) {
        accounts.lockForGroupChange(userId);
        Invite invite=invites.findByCode(rawCode).filter(i->!i.revoked()).orElseThrow(ApiFault::notFound);
        if(invite.expired(clock.instant()))throw new ApiFault(org.springframework.http.HttpStatus.GONE,"INVITE_EXPIRED","만료된 초대 코드예요");
        Group group=groups.lockById(invite.getGroupId()).filter(g->g.getArchivedAt()==null).orElseThrow(ApiFault::notFound);
        if(group.getType()!=GroupType.FRIEND)throw ApiFault.notFound();
        if(members.findByGroupIdAndUserIdAndLeftAtIsNull(group.getId(),userId).isPresent())
            throw ApiFault.conflict("ALREADY_MEMBER","이미 참여 중인 그룹이에요");
        groupService.checkLimit(userId);
        if(members.countByGroupIdAndLeftAtIsNull(group.getId())>=6)
            throw ApiFault.conflict("GROUP_FULL","그룹 인원이 가득 찼어요");
        String nickname=accounts.nicknameOf(userId);
        // ERD defines unique active display_name. Prevent a DB 500 on collisions.
        if(members.existsByGroupIdAndDisplayNameAndLeftAtIsNull(group.getId(),nickname))
            throw ApiFault.validation("같은 닉네임이 있는 그룹이에요. 닉네임을 변경해 주세요");
        GroupMember m=members.save(new GroupMember(group.getId(),userId,nickname,clock.instant()));
        events.publishEvent(new MemberJoinedEvent(group.getId(),m.getId(),userId));
        return groupService.detail(userId,group.getId());
    }
    private String newCode() {
        StringBuilder builder=new StringBuilder();
        for(int i=0;i<8;i++)builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return builder.toString();
    }
}
