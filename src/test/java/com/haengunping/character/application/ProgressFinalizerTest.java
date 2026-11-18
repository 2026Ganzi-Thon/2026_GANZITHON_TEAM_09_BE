package com.haengunping.character.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.haengunping.character.domain.CharacterKind;
import com.haengunping.character.domain.GroupCharacter;
import com.haengunping.character.infra.GroupCharacterRepository;
import com.haengunping.common.job.CatchUpResult;
import com.haengunping.common.time.ClockConfig;
import com.haengunping.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** CHR-02·03·07: 00:00 확정 처리. 날짜는 고정(2026-10월)이고 job_runs도 테스트마다 비운다. */
class ProgressFinalizerTest extends IntegrationTest {

    private static final LocalDate D1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate D2 = D1.plusDays(1);

    @Autowired
    ProgressFinalizer finalizer;
    @Autowired
    GroupCharacterRepository characters;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;

    private long group;
    private final List<Long> users = new ArrayList<>();

    @BeforeEach
    void setUp() {
        group = createGroup();
        characters.save(new GroupCharacter(group, CharacterKind.SEED, D1));
        jdbc.update("DELETE FROM job_runs WHERE job_name = 'FINALIZE_PROGRESS'");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM job_runs WHERE job_name = 'FINALIZE_PROGRESS'");
        jdbc.update("DELETE FROM daily_group_progress WHERE group_id = ?", group);
        jdbc.update("DELETE FROM characters WHERE group_id = ?", group);
        jdbc.update("DELETE FROM group_members WHERE group_id = ?", group);
        deleteGroup(group);
        for (long user : users) {
            jdbc.update("DELETE FROM daily_missions WHERE user_id = ?", user);
            deleteUser(user);
        }
    }

    @Test
    void CHR02_23시_59분_구성원_중_완료_수를_저장한다_그_전에_나간_사람은_빠진다() {
        long a = member(D1, null);
        long b = member(D1, null);
        long leaver = member(D1, at(D1, 23, 0));
        member(D1, null);
        complete(a, D1);
        complete(b, D1);
        complete(leaver, D1); // 완료하고 23시에 나감 → 분자·분모 모두 빠짐

        finalizer.finalizeUpTo(D1);

        assertThat(row(D1)).containsExactly(3, 2); // 4명 중 1명 나감 → 3명 중 2명
    }

    @Test
    void CHR03_밀린_이틀을_오래된_순으로_모두_확정하고_다시_돌려도_같다() {
        long a = member(D1, null);
        complete(a, D2);

        CatchUpResult first = finalizer.finalizeUpTo(D2);
        CatchUpResult second = finalizer.finalizeUpTo(D2);

        assertThat(first.ran()).containsExactly(D1, D2);
        assertThat(second.ran()).isEmpty();
        assertThat(row(D1)).containsExactly(1, 0);
        assertThat(row(D2)).containsExactly(1, 1);
    }

    @Test
    void CHR03_서버를_켜면_밀린_날짜를_어제까지_바로_확정한다() {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(2);
        jdbc.update("UPDATE characters SET started_on = ? WHERE group_id = ?", start, group);
        member(start, null);

        finalizer.catchUpOnStartup();

        assertThat(row(start)).containsExactly(1, 0);
        assertThat(row(today.minusDays(1))).containsExactly(1, 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_group_progress WHERE group_id = ? AND date = ?",
                Integer.class, group, today)).isZero();
    }

    @Test
    void CHR03_구성원이_0명인_날은_행을_만들지_않는다() {
        finalizer.finalizeUpTo(D1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_group_progress WHERE group_id = ?", Integer.class, group)).isZero();
    }

    @Test
    void CHR07_확정_99에_2가_더해지면_완성하고_다음_날부터_새_캐릭터가_0에서_시작한다() {
        // D1 이전 확정 합 99 = 1인 그룹 완료 49일(98) + 2명 중 1명 하루(1)
        LocalDate start = D1.minusDays(50);
        jdbc.update("UPDATE characters SET started_on = ? WHERE group_id = ?", start, group);
        for (int i = 0; i < 49; i++) {
            progress(start.plusDays(i), 1, 1);
        }
        progress(start.plusDays(49), 2, 1);
        jdbc.update("""
                INSERT INTO job_runs (job_name, run_key, status, started_at, finished_at)
                SELECT 'FINALIZE_PROGRESS', d::date, 'DONE', now(), now() FROM generate_series(?::date, ?::date, '1 day') d
                """, start, D1.minusDays(1));
        long a = member(start, null);
        complete(a, D1);

        finalizer.finalizeUpTo(D1);

        assertThat(jdbc.queryForObject("SELECT completed_on FROM characters WHERE group_id = ? AND started_on = ?",
                LocalDate.class, group, start)).isEqualTo(D1);
        assertThat(characters.findByGroupIdAndCompletedOnIsNull(group)).get()
                .satisfies(c -> assertThat(c.startedOn()).isEqualTo(D1.plusDays(1)));
    }

    @Test
    void 시작일보다_앞으로_되돌린_날짜는_예외_없이_건너뛴다() {
        assertThat(finalizer.finalizeUpTo(D1.minusDays(2)).ran()).isEmpty();
    }

    @Test
    void GRP12_전날_확정이_끝나야_비활성_정리를_허락한다() {
        member(D1, null);
        assertThat(finalizer.mayRemoveInactive(D1)).isFalse();

        finalizer.finalizeUpTo(D1);

        assertThat(finalizer.mayRemoveInactive(D1)).isTrue();
        assertThat(finalizer.mayRemoveInactive(D1.minusDays(1))).isTrue(); // 캐릭터 시작 전 날은 막지 않는다
    }

    private long member(LocalDate joinedOn, Instant leftAt) {
        long user = createUser();
        users.add(user);
        jdbc.update("INSERT INTO group_members (group_id, user_id, display_name, joined_at, left_at) VALUES (?, ?, ?, ?, ?)",
                group, user, "u" + user, Timestamp.from(joinedOn.atStartOfDay(ClockConfig.KST).toInstant()),
                leftAt == null ? null : Timestamp.from(leftAt));
        return user;
    }

    private void complete(long user, LocalDate day) {
        jdbc.update("INSERT INTO daily_missions (user_id, date, topic_id, completed_at) VALUES (?, ?, 1, now())", user, day);
    }

    private void progress(LocalDate day, int members, int completed) {
        jdbc.update("INSERT INTO daily_group_progress (group_id, date, member_count, completed_count, finalized_at) VALUES (?, ?, ?, ?, now())",
                group, day, members, completed);
    }

    private List<Integer> row(LocalDate day) {
        return jdbc.queryForObject("SELECT member_count, completed_count FROM daily_group_progress WHERE group_id = ? AND date = ?",
                (rs, n) -> List.of(rs.getInt(1), rs.getInt(2)), group, day);
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(ClockConfig.KST).toInstant();
    }
}
