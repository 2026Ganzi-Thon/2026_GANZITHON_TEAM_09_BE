
package com.haengunping.account.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private static final Instant NOW =
            Instant.parse("2026-11-17T12:00:00Z");

    private User createUser() {
        return new User("테스트", new byte[]{1, 2, 3}, NOW, false);
    }

    // 한글을 좋아하는 AI가 만들어준 검증 솔루션(test부분해결)
    @Test
    void 사용자_생성_초기상태_확인() {
        User user = createUser();

        assertNull(user.getId());
        assertEquals("테스트", user.getNickname());
        assertEquals(NOW, user.getCreatedAt());
        assertEquals(NOW, user.getLastSeenAt());

        assertTrue(user.isActive());
        assertFalse(user.isAdultConfirmed());
        assertFalse(user.hasSensitiveConsent());
        assertFalse(user.isDemo());
    }

    @Test
    void 데모_계정_생성() {
        User user = new User(
                "데모",
                new byte[]{4, 5, 6},
                NOW,
                true
        );

        assertTrue(user.isDemo());
        assertTrue(user.isActive());
    }

    @Test
    void 기본_생성자_확인() {
        User user = new User();

        assertNull(user.getId());
        assertNull(user.getNickname());
        assertFalse(user.isAdultConfirmed());
        assertFalse(user.hasSensitiveConsent());
    }

    @Test
    void 닉네임_변경() {
        User user = createUser();

        user.rename("새닉네임");

        assertEquals("새닉네임", user.getNickname());
    }

    @Test
    void 민감정보_동의_및_철회() {
        User user = createUser();

        assertFalse(user.hasSensitiveConsent());

        user.consent(true, NOW);

        assertTrue(user.hasSensitiveConsent());

        user.consent(false, NOW.plusSeconds(60));

        assertFalse(user.hasSensitiveConsent());
    }

    @Test
    void 성인_확인() {
        User user = createUser();

        assertFalse(user.isAdultConfirmed());

        user.confirmAdult();

        assertTrue(user.isAdultConfirmed());
    }

    @Test
    void 마지막_활동시간_1시간_미만이면_갱신하지_않음() {
        User user = createUser();

        user.touch(NOW.plusSeconds(3599));

        assertEquals(NOW, user.getLastSeenAt());
    }

    @Test
    void 마지막_활동시간_정확히_1시간이면_갱신() {
        User user = createUser();
        Instant later = NOW.plusSeconds(3600);

        user.touch(later);

        assertEquals(later, user.getLastSeenAt());
    }

    @Test
    void 마지막_활동시간_1시간_초과하면_갱신() {
        User user = createUser();
        Instant later = NOW.plusSeconds(7200);

        user.touch(later);

        assertEquals(later, user.getLastSeenAt());
    }

    @Test
    void 계정_삭제_시_개인정보_제거() {
        User user = createUser();
        user.consent(true, NOW);

        Instant deletedAt = NOW.plusSeconds(86400);
        user.delete(deletedAt);

        assertFalse(user.isActive());
        assertNull(user.getNickname());
        assertFalse(user.hasSensitiveConsent());
    }
}
