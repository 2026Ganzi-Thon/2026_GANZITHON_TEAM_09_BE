package com.haengunping.character.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haengunping.character.domain.CharacterKind;
import com.haengunping.character.domain.DailyGroupProgress;
import com.haengunping.character.domain.GroupCharacter;
import com.haengunping.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 확정 합 SQL이 도메인 계산(Growth)과 같은 값을 내는지, DB 제약이 규칙을 지키는지 확인한다. */
class CharacterRepositoryTest extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @Autowired
    GroupCharacterRepository characters;
    @Autowired
    DailyGroupProgressRepository progress;
    @Autowired
    JdbcTemplate jdbc;

    private final long group = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    private final long otherGroup = group - 1;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM daily_group_progress WHERE group_id IN (?, ?)", group, otherGroup);
        jdbc.update("DELETE FROM characters WHERE group_id IN (?, ?)", group, otherGroup);
    }

    @Test
    void CHR04_확정_합은_시작일_이후_그_그룹의_정수_합이다() {
        progress.save(new DailyGroupProgress(group, DAY.minusDays(1), 4, 4, Instant.EPOCH)); // 시작 전: 빠짐
        progress.save(new DailyGroupProgress(group, DAY, 4, 3, Instant.EPOCH));               // 1.5 = 90
        progress.save(new DailyGroupProgress(group, DAY.plusDays(1), 3, 2, Instant.EPOCH));   // 1.333… = 80
        progress.save(new DailyGroupProgress(otherGroup, DAY, 1, 1, Instant.EPOCH));          // 다른 그룹: 빠짐

        assertThat(progress.confirmedUnitsSince(group, DAY)).isEqualTo(170);
    }

    @Test
    void CHR04_확정_기록이_없으면_0() {
        assertThat(progress.confirmedUnitsSince(group, DAY)).isZero();
    }

    @Test
    void CHR04_완료_수가_구성원_수를_넘으면_DB가_거부한다() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO daily_group_progress (group_id, date, member_count, completed_count, finalized_at) VALUES (?, ?, 3, 4, now())",
                group, DAY)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void CHR01_그룹마다_진행_중_캐릭터는_1개() {
        GroupCharacter saved = characters.save(new GroupCharacter(group, CharacterKind.SEED, DAY));

        assertThat(characters.findByGroupIdAndCompletedOnIsNull(group)).get().satisfies(c -> {
            assertThat(c.id()).isEqualTo(saved.id());
            assertThat(c.groupId()).isEqualTo(group);
            assertThat(c.kind()).isEqualTo(CharacterKind.SEED);
            assertThat(c.startedOn()).isEqualTo(DAY);
            assertThat(c.completedOn()).isNull();
        });
        assertThatThrownBy(() -> characters.saveAndFlush(new GroupCharacter(group, CharacterKind.CLOVER, DAY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void CHR07_완성된_캐릭터는_도감에_완성_순으로_나온다() {
        jdbc.update("INSERT INTO characters (group_id, kind, started_on, completed_on) VALUES (?, 'CLOVER', ?, ?)", group, DAY, DAY.plusDays(40));
        jdbc.update("INSERT INTO characters (group_id, kind, started_on, completed_on) VALUES (?, 'SEED', ?, ?)", group, DAY.minusDays(60), DAY.minusDays(1));
        characters.save(new GroupCharacter(group, CharacterKind.SPROUT, DAY.plusDays(41)));

        assertThat(characters.findByGroupIdAndCompletedOnIsNotNullOrderByCompletedOnAsc(group))
                .extracting(GroupCharacter::kind).containsExactly(CharacterKind.SEED, CharacterKind.CLOVER);
    }
}
