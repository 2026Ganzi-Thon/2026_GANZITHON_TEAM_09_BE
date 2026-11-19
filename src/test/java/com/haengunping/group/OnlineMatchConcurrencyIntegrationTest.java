package com.haengunping.group;

import static org.assertj.core.api.Assertions.assertThat;

import com.haengunping.group.api.dto.MatchResponse;
import com.haengunping.group.application.OnlineMatchService;
import com.haengunping.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** PostgreSQL 16에서 실제 advisory lock/row lock을 거치는 온라인 매칭 동시성 테스트. */
class OnlineMatchConcurrencyIntegrationTest extends IntegrationTest {

    @Autowired private OnlineMatchService onlineMatchService;
    @Autowired private JdbcClient jdbc;

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void twoSimultaneousEntriesForOneSlotNeverOverfillGroup() throws Exception {
        // 사용자 5명이 참여 중인 ONLINE 그룹에 마지막 한 자리만 남긴다.
        List<Long> users = new ArrayList<>();
        Long groupId = null;
        try {
            groupId = jdbc.sql("""
                    INSERT INTO groups (type, name, created_at)
                    VALUES ('ONLINE', '동시매칭검증', now()) RETURNING id
                    """).query(Long.class).single();

            for (int i = 0; i < 5; i++) {
                long userId = createUser();
                users.add(userId);
                jdbc.sql("""
                        INSERT INTO group_members (group_id, user_id, display_name, joined_at)
                        VALUES (:groupId, :userId, :displayName, now())
                        """)
                        .param("groupId", groupId)
                        .param("userId", userId)
                        .param("displayName", "기존멤버" + i)
                        .update();
            }

            long first = createUser();
            long second = createUser();
            users.add(first);
            users.add(second);
            jdbc.sql("UPDATE users SET adult_confirmed = true WHERE id IN (:first, :second)")
                    .param("first", first)
                    .param("second", second)
                    .update();

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<MatchResponse> firstResult = executor.submit(
                        () -> enterAtSameTime(first, ready, start));
                Future<MatchResponse> secondResult = executor.submit(
                        () -> enterAtSameTime(second, ready, start));

                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                // 별도 스레드/트랜잭션에서 실제 PostgreSQL advisory lock을 경유한다.
                MatchResponse a = firstResult.get(20, TimeUnit.SECONDS);
                MatchResponse b = secondResult.get(20, TimeUnit.SECONDS);

                assertThat(List.of(a.status(), b.status()))
                        .containsExactlyInAnyOrder("MATCHED", "WAITING");
                MatchResponse matched = "MATCHED".equals(a.status()) ? a : b;
                MatchResponse waiting = "WAITING".equals(a.status()) ? a : b;
                assertThat(matched.groupId()).isEqualTo(groupId);
                assertThat(waiting.groupId()).isNull();
                assertThat(waiting.queuedAt()).isNotNull();

                long activeMembers = jdbc.sql("""
                        SELECT count(*) FROM group_members
                        WHERE group_id = :groupId AND left_at IS NULL
                        """)
                        .param("groupId", groupId)
                        .query(Long.class).single();
                assertThat(activeMembers).isEqualTo(6L);

                long matchedUsers = jdbc.sql("""
                        SELECT count(*) FROM group_members
                        WHERE group_id = :groupId AND user_id IN (:first, :second) AND left_at IS NULL
                        """)
                        .param("groupId", groupId)
                        .param("first", first)
                        .param("second", second)
                        .query(Long.class).single();
                assertThat(matchedUsers).isEqualTo(1L);

                long waitingUsers = jdbc.sql("""
                        SELECT count(*) FROM online_queue WHERE user_id IN (:first, :second)
                        """)
                        .param("first", first)
                        .param("second", second)
                        .query(Long.class).single();
                assertThat(waitingUsers).isEqualTo(1L);
            } finally {
                start.countDown(); // 준비 중 실패하더라도 대기 중인 작업을 풀어 준다.
                executor.shutdownNow();
                executor.awaitTermination(10, TimeUnit.SECONDS);
            }
        } finally {
            // IntegrationTest는 DB를 공유하므로 @Transactional 테스트 롤백 대신 직접 정리한다.
            for (long userId : users) {
                jdbc.sql("DELETE FROM online_queue WHERE user_id = :userId")
                        .param("userId", userId).update();
            }
            if (groupId != null) {
                jdbc.sql("DELETE FROM group_members WHERE group_id = :groupId")
                        .param("groupId", groupId).update();
                jdbc.sql("DELETE FROM groups WHERE id = :groupId")
                        .param("groupId", groupId).update();
            }
            for (long userId : users) {
                deleteUser(userId);
            }
        }
    }

    private MatchResponse enterAtSameTime(long userId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return onlineMatchService.enter(userId);
    }
}
