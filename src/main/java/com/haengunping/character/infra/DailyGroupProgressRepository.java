package com.haengunping.character.infra;

import com.haengunping.character.domain.DailyGroupProgress;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DailyGroupProgressRepository extends JpaRepository<DailyGroupProgress, DailyGroupProgress.Key> {

    /** from(캐릭터 시작일)부터의 확정 성장 합, 1/60 단위. 식은 Growth.dailyUnits와 같다(정수 나눗셈이 항상 정확). */
    @Query(nativeQuery = true, value = """
            SELECT COALESCE(SUM(120 * completed_count / member_count), 0)
            FROM daily_group_progress WHERE group_id = :groupId AND date >= :from
            """)
    long confirmedUnitsSince(long groupId, LocalDate from);

    /** 그날 확정 행. 이미 있으면 그대로 둔다(같은 날 다시 처리해도 결과가 같다). */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO daily_group_progress (group_id, date, member_count, completed_count, finalized_at)
            VALUES (:groupId, :date, :memberCount, :completedCount, :finalizedAt)
            ON CONFLICT (group_id, date) DO NOTHING
            """)
    int insertIfAbsent(long groupId, LocalDate date, int memberCount, int completedCount, Instant finalizedAt);
}
