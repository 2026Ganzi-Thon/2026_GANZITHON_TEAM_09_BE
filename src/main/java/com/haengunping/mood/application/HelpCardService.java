package com.haengunping.mood.application;

import com.haengunping.mood.Mood;
import com.haengunping.mood.domain.HelpCardPolicy;
import com.haengunping.mood.infra.HelpCardDismissalRepository;
import com.haengunping.mood.infra.MoodRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가벼운 도움 카드 표시 여부와 닫기(MOOD-04). */
@Service
@Transactional(readOnly = true)
public class HelpCardService {

    private final MoodRepository moods;
    private final HelpCardDismissalRepository dismissals;
    private final Clock clock;

    public HelpCardService(MoodRepository moods, HelpCardDismissalRepository dismissals, Clock clock) {
        this.moods = moods;
        this.dismissals = dismissals;
        this.clock = clock;
    }

    public boolean shouldShow(long userId) {
        Instant now = clock.instant();
        boolean hidden = dismissals.findById(userId)
                .map(d -> HelpCardPolicy.isHidden(d.dismissedAt(), now))
                .orElse(false);
        if (hidden) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        int lowDays = moods.countByUserIdAndDateBetweenAndMoodIn(userId, HelpCardPolicy.streakStart(today), today, Mood.LOW);
        return HelpCardPolicy.isLowStreak(lowDays);
    }

    @Transactional
    public void dismiss(long userId) {
        dismissals.upsert(userId, clock.instant());
    }
}
