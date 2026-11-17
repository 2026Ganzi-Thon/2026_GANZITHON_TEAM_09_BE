package com.haengunping.mission.infra;

import com.haengunping.mission.domain.DailyMission;
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

    @Query("SELECT m.topicId FROM DailyMission m WHERE m.userId = :userId AND m.date BETWEEN :from AND :to")
    List<Short> findTopicIds(long userId, LocalDate from, LocalDate to);
}
