package com.haengunping.group;

import com.haengunping.account.AccountQuery;
import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import com.haengunping.group.application.GroupService;
import com.haengunping.group.application.OnlineMatchService;
import com.haengunping.group.domain.Group;
import com.haengunping.group.domain.GroupMember;
import com.haengunping.group.domain.GroupType;
import com.haengunping.group.domain.OnlineQueueEntry;
import com.haengunping.group.infra.GroupMemberRepository;
import com.haengunping.group.infra.GroupRepository;
import com.haengunping.group.infra.OnlineAliasGenerator;
import com.haengunping.group.infra.OnlineMatchMutex;
import com.haengunping.group.infra.OnlineQueueRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OnlineMatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T09:00:00Z");

    private final OnlineMatchMutex mutex = mock(OnlineMatchMutex.class);
    private final OnlineQueueRepository queue = mock(OnlineQueueRepository.class);
    private final GroupRepository groups = mock(GroupRepository.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final GroupService groupService = mock(GroupService.class);
    private final AccountQuery accounts = mock(AccountQuery.class);
    private final OnlineAliasGenerator names = mock(OnlineAliasGenerator.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final OnlineMatchService service = new OnlineMatchService(
            mutex, queue, groups, members, groupService, accounts, names, events,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @BeforeEach
    void setUp() {
        when(accounts.isAdultConfirmed(anyLong())).thenReturn(true);
        when(accounts.isActive(anyLong())).thenReturn(true);
        when(groups.findByTypeAndArchivedAtIsNull(GroupType.ONLINE)).thenReturn(List.of());
        when(queue.findAllByOrderByQueuedAtAsc()).thenReturn(List.of());

        AtomicLong nextMemberId = new AtomicLong(900L);
        when(members.save(any(GroupMember.class))).thenAnswer(invocation -> {
            GroupMember persisted = mock(GroupMember.class);
            when(persisted.getId()).thenReturn(nextMemberId.getAndIncrement());
            return persisted;
        });
        when(names.next(anySet())).thenReturn("산책하는 수달");
    }

    @Test
    void 시연용_온라인그룹_4명일때_신규사용자를_즉시_매칭한다() {
        Group demo = onlineGroup(100L, true);
        when(groups.findByTypeAndArchivedAtIsNull(GroupType.ONLINE)).thenReturn(List.of(demo));
        when(members.countByGroupIdAndLeftAtIsNull(100L)).thenReturn(4L);
        when(groups.lockById(100L)).thenReturn(Optional.of(demo));
        when(members.findByGroupIdAndLeftAtIsNullOrderByJoinedAtAsc(100L))
                .thenReturn(existingMembers(100L, 4));

        var result = service.enter(9L);

        assertEquals("MATCHED", result.status());
        assertEquals(100L, result.groupId());
        assertNull(result.queuedAt());
        assertTrue(demo.isDemo());
        verify(queue, never()).saveAndFlush(any(OnlineQueueEntry.class));
        verify(groups, never()).saveAndFlush(any(Group.class));

        ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
        verify(members).save(captor.capture());
        assertEquals(100L, captor.getValue().getGroupId());
        assertEquals(9L, captor.getValue().getUserId());
        verify(events).publishEvent(isA(MemberJoinedEvent.class));
    }

    @Test
    void 참여가능한_그룹이_없으면_대기열에_들어간다() {
        waitingResponseFor(9L);

        var result = service.enter(9L);

        assertEquals("WAITING", result.status());
        assertNull(result.groupId());
        assertEquals(NOW, result.queuedAt());
        verify(queue).saveAndFlush(any(OnlineQueueEntry.class));
        verify(groups, never()).saveAndFlush(any(Group.class));
    }

    @Test
    void 여섯명으로_가득찬_그룹은_매칭대상에서_제외한다() {
        Group full = onlineGroup(200L, false);
        when(groups.findByTypeAndArchivedAtIsNull(GroupType.ONLINE)).thenReturn(List.of(full));
        when(members.countByGroupIdAndLeftAtIsNull(200L)).thenReturn(6L);
        waitingResponseFor(9L);

        assertEquals("WAITING", service.enter(9L).status());
        verify(groups, never()).lockById(200L);
        verify(members, never()).save(any(GroupMember.class));
    }

    @Test
    void 참여가능한_그룹이_여러개면_인원이_더_적은_그룹에_배정한다() {
        Group four = onlineGroup(300L, true);
        Group two = onlineGroup(301L, false);
        when(groups.findByTypeAndArchivedAtIsNull(GroupType.ONLINE))
                .thenReturn(List.of(four, two));
        when(members.countByGroupIdAndLeftAtIsNull(300L)).thenReturn(4L);
        when(members.countByGroupIdAndLeftAtIsNull(301L)).thenReturn(2L);
        when(groups.lockById(301L)).thenReturn(Optional.of(two));
        when(members.findByGroupIdAndLeftAtIsNullOrderByJoinedAtAsc(301L))
                .thenReturn(existingMembers(301L, 2));

        var result = service.enter(9L);

        assertEquals("MATCHED", result.status());
        assertEquals(301L, result.groupId());
        verify(groups).lockById(301L);
        verify(groups, never()).lockById(300L);
    }

    @Test
    void 대기자_네명이_되면_새_온라인그룹을_만든다() {
        List<OnlineQueueEntry> waiting = List.of(
                new OnlineQueueEntry(11L, NOW),
                new OnlineQueueEntry(12L, NOW.plusSeconds(1)),
                new OnlineQueueEntry(13L, NOW.plusSeconds(2)),
                new OnlineQueueEntry(14L, NOW.plusSeconds(3))
        );
        when(queue.findAllByOrderByQueuedAtAsc())
                .thenReturn(waiting, waiting, List.of());
        when(names.groupName()).thenReturn("아침 산책단");

        Group persisted = onlineGroup(500L, false);
        when(groups.saveAndFlush(any(Group.class))).thenReturn(persisted);
        when(groups.lockById(500L)).thenReturn(Optional.of(persisted));
        when(members.findByGroupIdAndLeftAtIsNullOrderByJoinedAtAsc(500L))
                .thenReturn(List.of());

        service.matchWaiting();

        ArgumentCaptor<Group> created = ArgumentCaptor.forClass(Group.class);
        verify(groups).saveAndFlush(created.capture());
        assertEquals(GroupType.ONLINE, created.getValue().getType());
        assertFalse(created.getValue().isDemo());
        assertEquals("아침 산책단", created.getValue().getName());
        verify(members, times(4)).save(any(GroupMember.class));
        verify(queue, times(4)).delete(any(OnlineQueueEntry.class));
        verify(events).publishEvent(isA(GroupCreatedEvent.class));
        verify(events, times(4)).publishEvent(isA(MemberJoinedEvent.class));
    }

    @Test
    void 성인확인이_없으면_매칭을_거부한다() {
        when(accounts.isAdultConfirmed(9L)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class, () -> service.enter(9L));

        assertEquals(ErrorCode.ADULT_CONFIRMATION_REQUIRED, error.code());
        verify(queue, never()).saveAndFlush(any(OnlineQueueEntry.class));
    }

    @Test
    void 이미_대기중이면_중복_매칭을_거부한다() {
        when(queue.existsById(9L)).thenReturn(true);

        ApiException error = assertThrows(ApiException.class, () -> service.enter(9L));

        assertEquals(ErrorCode.ALREADY_QUEUED, error.code());
        verify(queue, never()).saveAndFlush(any(OnlineQueueEntry.class));
    }

    @Test
    void 매칭_취소는_대기열에서_사용자를_삭제한다() {
        service.cancel(9L);

        verify(mutex).lock();
        verify(accounts).lockForGroupChange(9L);
        verify(queue).deleteById(9L);
    }

    private Group onlineGroup(long id, boolean demo) {
        Group group = mock(Group.class);
        when(group.getId()).thenReturn(id);
        when(group.getType()).thenReturn(GroupType.ONLINE);
        when(group.isDemo()).thenReturn(demo);
        return group;
    }

    private List<GroupMember> existingMembers(long groupId, int count) {
        return java.util.stream.LongStream.rangeClosed(1L, count)
                .mapToObj(i -> new GroupMember(
                        groupId, 100L + i, "기존 멤버 " + i, NOW))
                .toList();
    }

    private void waitingResponseFor(long userId) {
        when(queue.existsById(userId)).thenReturn(false, true);
        when(queue.findById(userId))
                .thenReturn(Optional.of(new OnlineQueueEntry(userId, NOW)));
    }
}
