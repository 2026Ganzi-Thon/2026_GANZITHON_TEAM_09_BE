package com.haengunping.mission.infra;

import com.haengunping.mission.domain.DailyMission;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DailyMissionRepository extends JpaRepository<DailyMission, DailyMission.Key> {

    // 첫 조회가 동시에 두 번 와도 PK 충돌(500) 없이 먼저 들어간 배정이 남는다(MIS-03).
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO daily_missions (user_id, date, topic_id) VALUES (:userId, :date, :topicId)
            ON CONFLICT (user_id, date) DO NOTHING
            """)
    void insertIfAbsent(long userId, LocalDate date, short topicId);

    @Modifying
    @Query("DELETE FROM DailyMission m WHERE m.userId = :userId")
    int deleteByUserId(long userId);

    Optional<DailyMission> findByUserIdAndDate(long userId, LocalDate date);

    /** 아직 완료하지 않았을 때만 완료 시각을 쓴다. 동시에 두 번 와도 행 잠금으로 1건만 1을 돌려준다(NFR-09). */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE daily_missions SET completed_at = :now
            WHERE user_id = :userId AND date = :date AND completed_at IS NULL
            """)
    int completeIfNotYet(long userId, LocalDate date, Instant now);

    @Query("""
            SELECT m FROM DailyMission m
            WHERE m.userId = :userId AND m.date BETWEEN :from AND :to AND m.completedAt IS NOT NULL
            ORDER BY m.date
            """)
    List<DailyMission> findCompleted(long userId, LocalDate from, LocalDate to);

    @Query("SELECT m.topicId FROM DailyMission m WHERE m.userId = :userId AND m.date BETWEEN :from AND :to")
    List<Short> findTopicIds(long userId, LocalDate from, LocalDate to);
}
