package com.haengunping.mood.infra;

import com.haengunping.mood.Mood;
import com.haengunping.mood.domain.DailyMood;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MoodRepository extends JpaRepository<DailyMood, DailyMood.Key> {

    // 조회 후 저장은 동시에 두 번 누르면 PK 충돌(500)이 난다. DB에서 한 문장으로 덮어쓴다(MOOD-02).
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO moods (user_id, date, mood, updated_at) VALUES (:userId, :date, :mood, :updatedAt)
            ON CONFLICT (user_id, date) DO UPDATE SET mood = EXCLUDED.mood, updated_at = EXCLUDED.updated_at
            """)
    void upsert(long userId, LocalDate date, String mood, Instant updatedAt);

    @Modifying
    @Query("DELETE FROM DailyMood m WHERE m.userId = :userId")
    int deleteByUserId(long userId);

    Optional<DailyMood> findByUserIdAndDate(long userId, LocalDate date);

    List<DailyMood> findByUserIdAndDateBetweenOrderByDateAsc(long userId, LocalDate from, LocalDate to);

    int countByUserIdAndDateBetweenAndMoodIn(long userId, LocalDate from, LocalDate to, Collection<Mood> moods);
}
