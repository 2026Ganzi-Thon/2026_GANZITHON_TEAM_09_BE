package com.haengunping.group;

import com.haengunping.group.domain.Group;
import com.haengunping.group.domain.GroupType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroupDomainTest {
    @Test void GRP01_그룹생성_및_보관() {
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        var group = new Group(GroupType.FRIEND, "산책 모임", false, now);
        assertNull(group.getId());
        assertEquals(GroupType.FRIEND, group.getType());
        assertEquals("산책 모임", group.getName());
        assertFalse(group.isDemo());
        assertNull(group.getArchivedAt());
        group.archive(now.plusSeconds(3600));
        assertEquals(now.plusSeconds(3600), group.getArchivedAt());
    }

    @Test void GRP01_데모그룹_구분() {
        var group = new Group(GroupType.SOLO, "나의 작은 공간", true,
                Instant.parse("2026-11-17T00:00:00Z"));
        assertTrue(group.isDemo());
        assertEquals(GroupType.SOLO, group.getType());
    }
}
