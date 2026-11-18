package com.haengunping.group;

import com.haengunping.group.domain.GroupMember;
import com.haengunping.group.domain.LeftReason;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroupMemberDomainTest {
    @Test void GRP11_탈퇴시_상태변경과_중복탈퇴시_최초기록보존() {
        Instant joined = Instant.parse("2026-11-16T00:00:00Z");
        Instant left = Instant.parse("2026-11-17T00:00:00Z");
        var member = new GroupMember(1, 4, "수달", joined);
        assertEquals(1L, member.getGroupId());
        assertEquals(4L, member.getUserId());
        assertNull(member.getId());
        assertNull(member.getLeftAt());
        member.leave(left, LeftReason.LEFT);
        member.leave(left.plusSeconds(86400), LeftReason.REPORTED);
        assertEquals(left, member.getLeftAt());
        assertEquals(LeftReason.LEFT, member.getLeftReason());
    }

    @Test void GRP11_그룹내_표시이름변경() {
        var member = new GroupMember(1, 4, "수달", Instant.parse("2026-11-16T00:00:00Z"));
        member.setDisplayName("토끼");
        assertEquals("토끼", member.getDisplayName());
    }
}
