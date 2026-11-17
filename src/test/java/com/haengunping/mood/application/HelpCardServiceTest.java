package com.haengunping.mood.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.haengunping.mood.Mood;
import com.haengunping.mood.infra.HelpCardDismissalRepository;
import com.haengunping.mood.infra.MoodRepository;
import com.haengunping.support.IntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** prd.md 6절 「낮은 기분 연속」 표를 오늘 기준 날짜로 옮겨 확인한다(오늘 = 표의 11/16). */
class HelpCardServiceTest extends IntegrationTest {

    @Autowired
    HelpCardService service;
    @Autowired
    MoodRepository moods;
    @Autowired
    HelpCardDismissalRepository dismissals;
    @Autowired
    Clock clock;
    @Autowired
    TransactionTemplate tx;

    private long me;

    @BeforeEach
    void createMe() {
        me = createUser();
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(s -> {
            moods.deleteByUserId(me);
            dismissals.deleteById(me);
            deleteUser(me);
        });
    }

    @Test
    void MOOD04_지쳐요_우울해요_외로워요_3일이면_표시() {
        moodsFromTwoDaysAgo(Mood.TIRED, Mood.DEPRESSED, Mood.LONELY);
        assertThat(service.shouldShow(me)).isTrue();
    }

    @Test
    void MOOD04_가운데_날을_입력하지_않으면_표시_안_함() {
        moodsFromTwoDaysAgo(Mood.TIRED, null, Mood.LONELY);
        assertThat(service.shouldShow(me)).isFalse();
    }

    @Test
    void MOOD04_가운데_날이_보통이면_표시_안_함() {
        moodsFromTwoDaysAgo(Mood.TIRED, Mood.OKAY, Mood.LONELY);
        assertThat(service.shouldShow(me)).isFalse();
    }

    @Test
    void MOOD04_오늘_기분이_없으면_어제까지_3일이어도_표시_안_함() {
        LocalDate today = LocalDate.now(clock);
        put(today.minusDays(3), Mood.TIRED);
        put(today.minusDays(2), Mood.TIRED);
        put(today.minusDays(1), Mood.TIRED);
        assertThat(service.shouldShow(me)).isFalse();
    }

    @Test
    void MOOD04_닫으면_숨기고_7일이_지나면_다시_표시() {
        moodsFromTwoDaysAgo(Mood.TIRED, Mood.TIRED, Mood.TIRED);

        service.dismiss(me);
        service.dismiss(me); // 두 번 눌러도 오류 없음
        assertThat(service.shouldShow(me)).isFalse();

        tx.executeWithoutResult(s -> dismissals.upsert(me, clock.instant().minus(Duration.ofDays(7))));
        assertThat(service.shouldShow(me)).isTrue();
    }

    private void moodsFromTwoDaysAgo(Mood dayBefore, Mood yesterday, Mood today) {
        LocalDate now = LocalDate.now(clock);
        put(now.minusDays(2), dayBefore);
        put(now.minusDays(1), yesterday);
        put(now, today);
    }

    private void put(LocalDate date, Mood mood) {
        if (mood != null) {
            tx.executeWithoutResult(s -> moods.upsert(me, date, mood.name(), Instant.EPOCH));
        }
    }
}
