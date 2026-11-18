package com.haengunping.character.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.haengunping.character.infra.GroupCharacterRepository;
import com.haengunping.group.GroupCreatedEvent;
import com.haengunping.support.IntegrationTest;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** A의 GroupService가 그룹 생성 트랜잭션 안에서 내는 GroupCreatedEvent를 그대로 흉내 낸다. */
class CharacterStarterTest extends IntegrationTest {

    @Autowired
    ApplicationEventPublisher events;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    GroupCharacterRepository characters;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;

    private long group;

    @BeforeEach
    void setUp() {
        group = createGroup();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM characters WHERE group_id = ?", group);
        deleteGroup(group);
    }

    @Test
    void CHR01_그룹을_만들면_오늘_시작하는_진행_중_캐릭터가_1개_생긴다() {
        tx.executeWithoutResult(s -> events.publishEvent(new GroupCreatedEvent(group)));

        assertThat(characters.findByGroupIdAndCompletedOnIsNull(group)).get().satisfies(c -> {
            assertThat(c.kind()).isNotNull();
            assertThat(c.startedOn()).isEqualTo(LocalDate.now(clock));
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM characters WHERE group_id = ?", Integer.class, group)).isOne();
    }
}
