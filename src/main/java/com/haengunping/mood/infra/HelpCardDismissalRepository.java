package com.haengunping.mood.infra;

import com.haengunping.mood.domain.HelpCardDismissal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HelpCardDismissalRepository extends JpaRepository<HelpCardDismissal, Long> {

    // 다시 닫으면 시각만 새로 쓴다. 두 번 눌러도 PK 충돌이 나지 않게 한 문장으로.
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO help_card_dismissals (user_id, dismissed_at) VALUES (:userId, :dismissedAt)
            ON CONFLICT (user_id) DO UPDATE SET dismissed_at = EXCLUDED.dismissed_at
            """)
    void upsert(long userId, Instant dismissedAt);

    @Modifying
    @Query("DELETE FROM HelpCardDismissal d WHERE d.userId = :userId")
    int deleteByUserId(long userId);
}
