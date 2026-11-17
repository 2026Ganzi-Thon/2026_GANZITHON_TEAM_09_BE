package com.haengunping.character.infra;

import com.haengunping.character.domain.DailyGroupProgress;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DailyGroupProgressRepository extends JpaRepository<DailyGroupProgress, DailyGroupProgress.Key> {

    /** from(캐릭터 시작일)부터의 확정 성장 합, 1/60 단위. 식은 Growth.dailyUnits와 같다(정수 나눗셈이 항상 정확). */
    @Query(nativeQuery = true, value = """
            SELECT COALESCE(SUM(120 * completed_count / member_count), 0)
            FROM daily_group_progress WHERE group_id = :groupId AND date >= :from
            """)
    long confirmedUnitsSince(long groupId, LocalDate from);
}
