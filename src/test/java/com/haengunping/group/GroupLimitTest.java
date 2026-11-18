package com.haengunping.group;

import com.haengunping.account.AccountQuery;
import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import com.haengunping.group.application.GroupService;
import com.haengunping.group.infra.GroupMemberRepository;
import com.haengunping.group.infra.GroupRepository;
import com.haengunping.group.infra.OnlineQueueRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupLimitTest {
    private GroupService service(GroupMemberRepository members, OnlineQueueRepository queue) {
        return new GroupService(mock(GroupRepository.class), members, queue,
                mock(AccountQuery.class), mock(ObjectProvider.class),
                Clock.systemUTC(), mock(ApplicationEventPublisher.class));
    }

    @Test void GRP02_활성그룹_세개면_참여불가() {
        var members = mock(GroupMemberRepository.class);
        var queue = mock(OnlineQueueRepository.class);
        when(members.countByUserIdAndLeftAtIsNull(9L)).thenReturn(3L);
        ApiException error = assertThrows(ApiException.class, () -> service(members, queue).checkLimit(9L));
        assertEquals(ErrorCode.GROUP_LIMIT, error.code());
    }

    @Test void GRP02_두개그룹과_매칭대기열_하나면_참여불가() {
        var members = mock(GroupMemberRepository.class);
        var queue = mock(OnlineQueueRepository.class);
        when(members.countByUserIdAndLeftAtIsNull(9L)).thenReturn(2L);
        when(queue.existsById(9L)).thenReturn(true);
        ApiException error = assertThrows(ApiException.class, () -> service(members, queue).checkLimit(9L));
        assertEquals(ErrorCode.GROUP_LIMIT, error.code());
    }

    @Test void GRP02_두개그룹이고_대기열이_없으면_참여가능() {
        var members = mock(GroupMemberRepository.class);
        var queue = mock(OnlineQueueRepository.class);
        when(members.countByUserIdAndLeftAtIsNull(9L)).thenReturn(2L);
        assertDoesNotThrow(() -> service(members, queue).checkLimit(9L));
    }
}
