package com.haengunping.check.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.haengunping.check.CheckQuery;
import com.haengunping.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 다른 모듈에 여는 창구: HELP-06의 "기준 이상" 판단과 NOTI-03의 "이번 주에 한 사람" 목록. */
class CheckQueryTest extends IntegrationTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 11, 16);
    private static final LocalDate PREV_WEEK = LocalDate.of(2026, 11, 9);

    @Autowired
    CheckQuery query;
    @Autowired
    JdbcTemplate jdbc;

    private long me;
    private long other;

    @BeforeEach
    void setUp() {
        me = createUser();
        other = createUser();
    }

    @AfterEach
    void cleanUp() {
        for (long id : new long[] {me, other}) {
            jdbc.update("DELETE FROM checks WHERE user_id = ?", id);
            deleteUser(id);
        }
    }

    @Test
    void HELP06_우울이나_불안_결과가_기간_안에_있으면_true() {
        Instant now = Instant.now();
        done(me, WEEK, "DEPRESSION", now.minus(1, ChronoUnit.DAYS));

        assertThat(query.aboveThresholdSince(me, now.minus(7, ChronoUnit.DAYS))).isTrue();
    }

    @Test
    void HELP06_불안만_있어도_true_둘_다여도_true() {
        Instant now = Instant.now();
        done(me, WEEK, "ANXIETY", now);
        done(other, WEEK, "DEPRESSION,ANXIETY", now);

        assertThat(query.aboveThresholdSince(me, now.minus(7, ChronoUnit.DAYS))).isTrue();
        assertThat(query.aboveThresholdSince(other, now.minus(7, ChronoUnit.DAYS))).isTrue();
    }

    @Test
    void HELP06_STABLE이나_LOW_MOOD_DAYS만_있으면_false() {
        Instant now = Instant.now();
        done(me, WEEK, "STABLE", now);
        done(other, WEEK, "LOW_MOOD_DAYS", now);

        assertThat(query.aboveThresholdSince(me, now.minus(7, ChronoUnit.DAYS))).isFalse();
        assertThat(query.aboveThresholdSince(other, now.minus(7, ChronoUnit.DAYS))).isFalse();
    }

    @Test
    void HELP06_닫기만_했거나_기록이_없으면_false() {
        dismissed(me, WEEK, Instant.now());

        assertThat(query.aboveThresholdSince(me, Instant.now().minus(7, ChronoUnit.DAYS))).isFalse();
        assertThat(query.aboveThresholdSince(other, Instant.now().minus(7, ChronoUnit.DAYS))).isFalse();
    }

    @Test
    void HELP06_기간보다_오래된_결과는_세지_않고_경계_시각은_포함한다() {
        Instant since = Instant.parse("2026-11-20T00:00:00Z");
        done(me, PREV_WEEK, "DEPRESSION", since.minusSeconds(1));
        done(other, WEEK, "DEPRESSION", since);

        assertThat(query.aboveThresholdSince(me, since)).isFalse();
        assertThat(query.aboveThresholdSince(other, since)).isTrue();
    }

    @Test
    void HELP06_다른_사람_결과는_내_판단에_쓰지_않는다() {
        done(other, WEEK, "DEPRESSION", Instant.now());

        assertThat(query.aboveThresholdSince(me, Instant.now().minus(7, ChronoUnit.DAYS))).isFalse();
    }

    @Test
    void NOTI03_이번_주에_제출했거나_닫은_사람을_돌려준다() {
        done(me, WEEK, "STABLE", Instant.now());
        dismissed(other, WEEK, Instant.now());

        assertThat(query.usersWithRecord(WEEK, List.of(me, other))).containsExactlyInAnyOrder(me, other);
    }

    @Test
    void NOTI03_다른_주_기록이나_묻지_않은_사람은_돌려주지_않는다() {
        done(me, PREV_WEEK, "STABLE", Instant.now());
        done(other, WEEK, "STABLE", Instant.now());

        assertThat(query.usersWithRecord(WEEK, List.of(me))).isEmpty();
        assertThat(query.usersWithRecord(WEEK, List.of(me, other))).containsExactly(other);
        assertThat(query.usersWithRecord(WEEK, List.of(me))).doesNotContain(other);
    }

    @Test
    void NOTI03_빈_목록이면_빈_결과() {
        assertThat(query.usersWithRecord(WEEK, List.of())).isEmpty();
    }

    private void done(long userId, LocalDate week, String codes, Instant at) {
        jdbc.update("INSERT INTO checks (user_id, week_start, status, result_codes, created_at) "
                + "VALUES (?, ?, 'DONE', CAST(? AS varchar(15)[]), ?::timestamptz)",
                userId, week, "{" + codes + "}", at.toString());
    }

    private void dismissed(long userId, LocalDate week, Instant at) {
        jdbc.update("INSERT INTO checks (user_id, week_start, status, created_at) VALUES (?, ?, 'DISMISSED', ?::timestamptz)",
                userId, week, at.toString());
    }
}
