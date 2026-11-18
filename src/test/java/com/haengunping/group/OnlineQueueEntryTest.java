package com.haengunping.group;

import com.haengunping.group.domain.OnlineQueueEntry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnlineQueueEntryTest {
    @Test void GRP10_대기열_등록시각과_사용자확인() {
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        var entry = new OnlineQueueEntry(22L, now);
        assertEquals(22L, entry.getUserId());
        assertEquals(now, entry.getQueuedAt());
    }
}
