package com.haengunping.check.infra;

import com.haengunping.check.domain.CheckResult;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * checks 테이블(민감정보). 쓰기는 모두 {@code ON CONFLICT DO NOTHING}이라 같은 주에 두 번 들어가지 않고,
 * 충돌해도 예외가 나지 않아 호출한 트랜잭션이 깨지지 않는다. 영향 행 수로 들어갔는지 판정한다.
 */
@Repository
public class CheckRepository {

    public static final String DONE = "DONE";
    public static final String DISMISSED = "DISMISSED";

    private final JdbcClient jdbc;

    CheckRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 그 주 기록의 상태(DONE·DISMISSED). 기록이 없으면 비어 있다. */
    public Optional<String> statusOf(long userId, LocalDate weekStart) {
        return jdbc.sql("SELECT status FROM checks WHERE user_id = :userId AND week_start = :weekStart")
                .param("userId", userId)
                .param("weekStart", weekStart)
                .query(String.class)
                .optional();
    }

    /** 제출 기록(결과만)을 넣는다. 그 주 기록이 이미 있으면 0. */
    public int insertDone(long userId, LocalDate weekStart, List<CheckResult> results, Instant at) {
        // 값은 enum 이름뿐이라 배열 리터럴로 만들어도 안전하다
        String codes = results.stream().map(CheckResult::name).collect(Collectors.joining(",", "{", "}"));
        return jdbc.sql("""
                        INSERT INTO checks (user_id, week_start, status, result_codes, created_at)
                        VALUES (:userId, :weekStart, 'DONE', CAST(:codes AS varchar(15)[]), :at)
                        ON CONFLICT (user_id, week_start) DO NOTHING
                        """)
                .param("userId", userId)
                .param("weekStart", weekStart)
                .param("codes", codes)
                .param("at", utc(at))
                .update();
    }

    /** 닫은 기록을 넣는다. 그 주 기록이 이미 있으면 0(그대로 둔다). */
    public int insertDismissed(long userId, LocalDate weekStart, Instant at) {
        return jdbc.sql("""
                        INSERT INTO checks (user_id, week_start, status, created_at)
                        VALUES (:userId, :weekStart, 'DISMISSED', :at)
                        ON CONFLICT (user_id, week_start) DO NOTHING
                        """)
                .param("userId", userId)
                .param("weekStart", weekStart)
                .param("at", utc(at))
                .update();
    }

    public boolean existsAboveThresholdSince(long userId, Instant since) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM checks
                            WHERE user_id = :userId AND status = 'DONE' AND created_at >= :since
                              AND result_codes && ARRAY['DEPRESSION', 'ANXIETY']::varchar(15)[]
                        )
                        """)
                .param("userId", userId)
                .param("since", utc(since))
                .query(Boolean.class)
                .single();
    }

    public Set<Long> userIdsWithRecord(LocalDate weekStart, Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT user_id FROM checks WHERE week_start = :weekStart AND user_id IN (:userIds)")
                .param("weekStart", weekStart)
                .param("userIds", userIds)
                .query(Long.class)
                .list());
    }

    /** 계정 삭제·기록 삭제(ACC-07·08). */
    public int deleteByUserId(long userId) {
        return jdbc.sql("DELETE FROM checks WHERE user_id = :userId").param("userId", userId).update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
