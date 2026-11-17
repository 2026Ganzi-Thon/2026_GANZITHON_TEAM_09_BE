package com.haengunping.mood.application;

import com.haengunping.mood.Mood;
import com.haengunping.mood.MoodQuery;
import com.haengunping.mood.domain.DailyMood;
import com.haengunping.mood.domain.MoodPeriod;
import com.haengunping.mood.infra.MoodRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MoodService implements MoodQuery {

    private final MoodRepository moods;
    private final Clock clock;

    public MoodService(MoodRepository moods, Clock clock) {
        this.moods = moods;
        this.clock = clock;
    }

    /** 오늘(KST) 기분 저장. 같은 날 다시 보내면 덮어쓴다(MOOD-02). */
    @Transactional
    public DailyMood saveToday(long userId, Mood mood) {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        moods.upsert(userId, today, mood.name(), now);
        return new DailyMood(userId, today, mood, now);
    }

    /** 내 기분 기록, 날짜 오름차순(MOOD-05). */
    public List<DailyMood> list(long userId, MoodPeriod period) {
        return moods.findByUserIdAndDateBetweenOrderByDateAsc(userId, period.from(), period.to());
    }

    @Override
    public Optional<Mood> moodOn(long userId, LocalDate date) {
        return moods.findByUserIdAndDate(userId, date).map(DailyMood::mood);
    }

    @Override
    public int lowMoodDaysInWeek(long userId, LocalDate weekStart) {
        return moods.countByUserIdAndDateBetweenAndMoodIn(userId, weekStart, weekStart.plusDays(6), Mood.LOW);
    }
}
