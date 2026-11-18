package com.haengunping.group;

import com.haengunping.group.domain.BlockId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockIdTest {
    @Test void GRP12_차단_복합키_동등성() {
        var a = new BlockId(1L, 2L);
        var b = new BlockId(1L, 2L);
        var other = new BlockId(2L, 1L);
        assertEquals(a, a);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, other);
        assertNotEquals(a, null);
        assertNotEquals(a, "1:2");
    }
}
