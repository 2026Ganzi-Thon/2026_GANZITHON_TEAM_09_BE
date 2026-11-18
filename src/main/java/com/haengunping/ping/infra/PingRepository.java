package com.haengunping.ping.infra;

import com.haengunping.ping.domain.Ping;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PingRepository extends JpaRepository<Ping, Ping.Key> {

    /** 하루 1개. 1이면 보냄, 0이면 오늘 이미 보냈다(동시 요청도 1건만). */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO pings (user_id, date, emoji, created_at) VALUES (:userId, :date, :emoji, :createdAt)
            ON CONFLICT (user_id, date) DO NOTHING
            """)
    int insertIfAbsent(long userId, LocalDate date, String emoji, Instant createdAt);

    List<Ping> findByDateAndUserIdIn(LocalDate date, Collection<Long> userIds);

    @Modifying
    @Query("DELETE FROM Ping p WHERE p.userId = :userId")
    int deleteByUserId(long userId);
}
