package com.haengunping.group;

import com.haengunping.group.domain.Invite;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InviteDomainTest {
    @Test void GRP04_초대코드_7일만료_경계() {
        Instant start = Instant.parse("2026-11-17T00:00:00Z");
        Instant expires = start.plusSeconds(7 * 24 * 3600L);
        var invite = new Invite(1, "K7MP2QXR", 5, expires);
        assertEquals(1L, invite.getGroupId());
        assertEquals("K7MP2QXR", invite.getCode());
        assertEquals(expires, invite.getExpiresAt());
        assertFalse(invite.expired(expires.minusNanos(1)));
        assertTrue(invite.expired(expires));
        assertTrue(invite.expired(expires.plusSeconds(1)));
    }

    @Test void GRP04_초대취소_상태() {
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        var invite = new Invite(1, "K7MP2QXR", 5, now.plusSeconds(3600));
        assertFalse(invite.revoked());
        invite.revoke(now);
        assertTrue(invite.revoked());
        assertDoesNotThrow(() -> invite.revoke(now.plusSeconds(60)));
        assertTrue(invite.revoked());
    }
}
