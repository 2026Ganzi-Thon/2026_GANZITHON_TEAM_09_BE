package com.haengunping.group;

import com.haengunping.account.AccountQuery;
import com.haengunping.group.application.GroupService;
import com.haengunping.group.application.InactiveMemberCleanupJob;
import com.haengunping.group.application.OnlineMatchService;
import com.haengunping.group.domain.GroupMember;
import com.haengunping.group.infra.GroupMemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InactiveMemberCleanupJobTest {

    // 2026-11-19 00:10 KST = 2026-11-18 15:10 UTC
    private static final Instant NOW = Instant.parse("2026-11-18T15:10:00Z");
    private static final LocalDate PREVIOUS_KST_DATE = LocalDate.of(2026, 11, 18);

    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final AccountQuery accounts = mock(AccountQuery.class);
    private final GroupService groups = mock(GroupService.class);
    private final OnlineMatchService matcher = mock(OnlineMatchService.class);
    private final GroupCleanupGate gate = mock(GroupCleanupGate.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<GroupCleanupGate> gateProvider = mock(ObjectProvider.class);

    private InactiveMemberCleanupJob job() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new InactiveMemberCleanupJob(members, accounts, gateProvider,
                groups, matcher, clock);
    }

    private static GroupMember member(long groupId, long userId) {
        return new GroupMember(groupId, userId, "별칭" + userId, NOW.minus(Duration.ofDays(10)));
    }

    @Test
    void GRP12_00시10분_KST_예약실행으로_설정되어있다() throws Exception {
        Scheduled scheduled = InactiveMemberCleanupJob.class
                .getMethod("execute").getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("0 10 0 * * *", scheduled.cron());
        assertEquals("Asia/Seoul", scheduled.zone());
    }

    @Test
    void GRP12_성장확정_게이트가_없으면_정리와_재매칭을_건너뛴다() {
        // gateProvider.getIfAvailable()은 Mockito 기본값인 null을 반환한다.
        job().execute();

        verify(gateProvider).getIfAvailable();
        verifyNoInteractions(members, accounts, groups, matcher);
    }

    @Test
    void GRP12_전날_성장확정이_안됐으면_정리와_재매칭을_건너뛴다() {
        when(gateProvider.getIfAvailable()).thenReturn(gate);
        when(gate.mayRemoveInactive(PREVIOUS_KST_DATE)).thenReturn(false);

        job().execute();

        // Clock 자체는 UTC여도 KST의 '어제' 날짜가 전달되어야 한다.
        verify(gate).mayRemoveInactive(PREVIOUS_KST_DATE);
        verifyNoInteractions(members, accounts, groups, matcher);
    }

    @Test
    void GRP12_7일_이전과_정확히_7일전은_제거하고_최근접속자는_유지한다() {
        when(gateProvider.getIfAvailable()).thenReturn(gate);
        when(gate.mayRemoveInactive(PREVIOUS_KST_DATE)).thenReturn(true);

        GroupMember old = member(10L, 101L);
        GroupMember boundary = member(10L, 102L);
        GroupMember recent = member(20L, 103L);
        GroupMember noRecord = member(20L, 104L);
        when(members.activeOnlineMembers()).thenReturn(List.of(old, boundary, recent, noRecord));

        Instant cutoff = NOW.minus(Duration.ofDays(7));
        when(accounts.lastSeenAt(101L)).thenReturn(Optional.of(cutoff.minusSeconds(1)));
        when(accounts.lastSeenAt(102L)).thenReturn(Optional.of(cutoff));
        when(accounts.lastSeenAt(103L)).thenReturn(Optional.of(cutoff.plusSeconds(1)));
        when(accounts.lastSeenAt(104L)).thenReturn(Optional.empty());

        job().execute();

        verify(groups).inactiveRemove(10L, 101L);
        verify(groups).inactiveRemove(10L, 102L);
        verify(groups, never()).inactiveRemove(20L, 103L);
        verify(groups, never()).inactiveRemove(20L, 104L);

        var order = inOrder(groups, matcher);
        order.verify(groups).inactiveRemove(10L, 101L);
        order.verify(groups).inactiveRemove(10L, 102L);
        order.verify(matcher).matchWaiting();
        verifyNoMoreInteractions(groups, matcher);
    }

    @Test
    void GRP12_정리할_온라인구성원이_없어도_대기자_재매칭을_수행한다() {
        when(gateProvider.getIfAvailable()).thenReturn(gate);
        when(gate.mayRemoveInactive(PREVIOUS_KST_DATE)).thenReturn(true);
        when(members.activeOnlineMembers()).thenReturn(List.of());

        job().execute();

        verify(gate).mayRemoveInactive(PREVIOUS_KST_DATE);
        verifyNoInteractions(groups, accounts);
        verify(matcher).matchWaiting();
    }
}
