package com.haengunping.notification.infra;

import com.haengunping.notification.domain.PushSubscription;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    // 같은 endpoint(같은 브라우저)는 덮어쓴다. 계정을 새로 만든 같은 기기면 새 사용자에게 옮겨 간다.
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, created_at)
            VALUES (:userId, :endpoint, :p256dh, :auth, :createdAt)
            ON CONFLICT (endpoint) DO UPDATE
            SET user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth, created_at = EXCLUDED.created_at
            """)
    void upsert(long userId, String endpoint, String p256dh, String auth, Instant createdAt);

    // 남의 구독은 지우지 못하게 userId를 함께 건다.
    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.userId = :userId AND s.endpoint = :endpoint")
    int deleteByUserIdAndEndpoint(long userId, String endpoint);

    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.userId = :userId")
    int deleteByUserId(long userId);

    List<PushSubscription> findByUserId(long userId);
}
