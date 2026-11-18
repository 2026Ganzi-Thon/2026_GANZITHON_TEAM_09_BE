package com.haengunping.group;

import com.haengunping.group.infra.OnlineAliasGenerator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnlineAliasGeneratorTest {
    @Test void GRP10_기존별칭과_겹치지않음() {
        var generator = new OnlineAliasGenerator();
        String first = generator.next(Set.of());
        String second = generator.next(Set.of(first));
        assertNotEquals(first, second);
        assertTrue(second.length() <= 20);
        assertFalse(generator.groupName().isBlank());
    }
}
