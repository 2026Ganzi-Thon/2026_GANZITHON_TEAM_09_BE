package com.haengunping.mood.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.haengunping.mood.Mood;
import com.haengunping.mood.domain.DailyMood;
import com.haengunping.mood.domain.MoodPeriod;
import com.haengunping.mood.infra.MoodRepository;
import com.haengunping.support.IntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 PostgreSQL로 저장·조회·MoodQuery를 확인한다. 테스트마다 사용자를 새로 만들어 다른 테스트와 섞이지 않게 한다. */
class MoodServiceTest extends IntegrationTest {

    // 2026-11-16(월) ~ 11-22(일)
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    @Autowired
    MoodService service;
    @Autowired
    MoodRepository repository;
    @Autowired
    Clock clock;
    @Autowired
    TransactionTemplate tx;

    private long me;
    private long other;

    @BeforeEach
    void createUsers() {
        me = createUser();
        other = createUser();
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(s -> {
            repository.deleteByUserId(me);
            repository.deleteByUserId(other);
            deleteUser(me);
            deleteUser(other);
        });
    }

    @Test
    void MOOD02_같은_날_두_번_저장하면_1건_나중_값() {
        service.saveToday(me, Mood.GOOD);
        service.saveToday(me, Mood.LONELY);

        LocalDate today = LocalDate.now(clock);
        List<DailyMood> saved = service.list(me, new MoodPeriod(today, today));
        assertThat(saved).singleElement().satisfies(m -> {
            assertThat(m.date()).isEqualTo(today);
            assertThat(m.mood()).isEqualTo(Mood.LONELY);
        });
    }

    @Test
    void HELP06_오늘까지_3일_연속_외로워요일_때만_신호() {
        LocalDate today = MONDAY.plusDays(2);
        put(me, today.minusDays(2), Mood.LONELY);
        put(me, today.minusDays(1), Mood.LONELY);
        put(me, today, Mood.LONELY);
        put(other, today.minusDays(2), Mood.LONELY);
        put(other, today, Mood.LONELY); // 가운데 날 기록 없음
        assertThat(service.lonelyThreeDaysUpTo(me, today)).isTrue();
        assertThat(service.lonelyThreeDaysUpTo(other, today)).isFalse();

        put(me, today.minusDays(1), Mood.DEPRESSED); // 낮은 기분이어도 외로워요가 아니면 끊김
        assertThat(service.lonelyThreeDaysUpTo(me, today)).isFalse();
    }

    @Test
    void MOOD05_기간_안의_기록만_날짜순으로_준다() {
        put(me, MONDAY.plusDays(2), Mood.CALM);
        put(me, MONDAY, Mood.GOOD);
        put(me, MONDAY.minusDays(1), Mood.OKAY);
        put(me, MONDAY.plusDays(3), Mood.TIRED);

        List<DailyMood> found = service.list(me, new MoodPeriod(MONDAY, MONDAY.plusDays(2)));

        assertThat(found).extracting(DailyMood::date).containsExactly(MONDAY, MONDAY.plusDays(2));
    }

    @Test
    void SEC03_다른_사용자의_기록은_보이지_않는다() {
        put(other, MONDAY, Mood.DEPRESSED);

        assertThat(service.list(me, new MoodPeriod(MONDAY, MONDAY))).isEmpty();
        assertThat(service.moodOn(me, MONDAY)).isEmpty();
        assertThat(service.lowMoodDaysInWeek(me, MONDAY)).isZero();
    }

    @Test
    void 그날_기분을_준다_입력하지_않은_날은_비어_있다() {
        put(me, MONDAY, Mood.CALM);

        assertThat(service.moodOn(me, MONDAY)).contains(Mood.CALM);
        assertThat(service.moodOn(me, MONDAY.plusDays(1))).isEmpty();
    }

    @Test
    void CHK03_이번_주_월요일부터_일요일까지_낮은_기분_날만_센다() {
        put(me, MONDAY.minusDays(1), Mood.TIRED);      // 지난주 일요일: 빠짐
        put(me, MONDAY, Mood.TIRED);
        put(me, MONDAY.plusDays(1), Mood.GOOD);        // 낮은 기분 아님
        put(me, MONDAY.plusDays(2), Mood.DEPRESSED);
        put(me, MONDAY.plusDays(6), Mood.LONELY);      // 이번 주 일요일: 포함
        put(me, MONDAY.plusDays(7), Mood.LONELY);      // 다음 주 월요일: 빠짐

        assertThat(service.lowMoodDaysInWeek(me, MONDAY)).isEqualTo(3);
    }

    private void put(long userId, LocalDate date, Mood mood) {
        tx.executeWithoutResult(s -> repository.upsert(userId, date, mood.name(), Instant.EPOCH));
    }
}
