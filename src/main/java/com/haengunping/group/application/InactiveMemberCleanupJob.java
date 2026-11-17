package com.haengunping.group.application;
import com.haengunping.account.AccountQuery;
import com.haengunping.group.GroupCleanupGate;
import com.haengunping.group.infra.GroupMemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** Inactive removal must run only after B finalized yesterday's character growth. */
@Component
public class InactiveMemberCleanupJob {
    private static final Logger log=LoggerFactory.getLogger(InactiveMemberCleanupJob.class);
    private final GroupMemberRepository members;
    private final AccountQuery accounts;
    private final ObjectProvider<GroupCleanupGate> gate;
    private final GroupService groups;
    private final OnlineMatchService matcher;
    private final Clock clock;
    public InactiveMemberCleanupJob(GroupMemberRepository members,AccountQuery accounts,ObjectProvider<GroupCleanupGate> gate,
            GroupService groups,OnlineMatchService matcher,Clock clock) {
        this.members=members;this.accounts=accounts;this.gate=gate;this.groups=groups;this.matcher=matcher;this.clock=clock;
    }
    @Scheduled(cron="0 10 0 * * *",zone="Asia/Seoul")
    public void execute() {
        LocalDate yesterday=LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))).minusDays(1);
        GroupCleanupGate required=gate.getIfAvailable();
        if(required==null || !required.mayRemoveInactive(yesterday)) {
            log.warn("Skipping group cleanup: FINALIZE_PROGRESS is not confirmed for {}",yesterday);
            return;
        }
        var cutoff=clock.instant().minus(Duration.ofDays(7));
        for(var member:members.activeOnlineMembers()) {
            var last=accounts.lastSeenAt(member.getUserId());
            if(last.isPresent() && !last.get().isAfter(cutoff))groups.inactiveRemove(member.getGroupId(),member.getUserId());
        }
        matcher.matchWaiting();
    }
}
