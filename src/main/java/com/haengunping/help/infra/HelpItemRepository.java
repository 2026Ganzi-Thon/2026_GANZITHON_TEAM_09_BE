package com.haengunping.help.infra;

import com.haengunping.help.domain.HelpItem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HelpItemRepository {

    private final JdbcClient jdbc;

    HelpItemRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 구역 순서(24시간 → 상담 → 들를 곳), 구역 안에서는 sort_order 순. 24시간 채널이 항상 맨 앞이다(HELP-07). */
    public List<HelpItem> findAllInDisplayOrder() {
        return jdbc.sql("""
                        SELECT id, section, kind, name, summary, target, note, phone, url, address,
                               apply_method, deadline, rules::text AS rules, source_service_id
                        FROM help_items
                        ORDER BY CASE section WHEN 'CRISIS_24H' THEN 0 WHEN 'COUNSELING' THEN 1 ELSE 2 END,
                                 sort_order, id
                        """)
                .query((rs, n) -> new HelpItem(
                        rs.getString("id"),
                        rs.getString("section"),
                        rs.getString("kind"),
                        rs.getString("name"),
                        rs.getString("summary"),
                        rs.getString("target"),
                        rs.getString("note"),
                        rs.getString("phone"),
                        rs.getString("url"),
                        rs.getString("address"),
                        rs.getString("apply_method"),
                        rs.getString("deadline"),
                        rs.getString("rules"),
                        rs.getString("source_service_id")))
                .list();
    }

    /** 목록이 마지막으로 바뀐 날(KST). 응답의 version. */
    public String lastUpdatedDate() {
        return jdbc.sql("""
                        SELECT to_char(max(updated_at) AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD') FROM help_items
                        """)
                .query(String.class)
                .single();
    }

    /** 공공서비스 API 동기화가 마지막으로 성공한 시각. 아직 없으면 비어 있다. */
    public Optional<Instant> lastSyncedAt() {
        return jdbc.sql("SELECT max(finished_at) FROM service_sync_log WHERE status = 'SUCCESS'")
                .query((rs, n) -> rs.getObject(1, OffsetDateTime.class))
                .optional()
                .map(OffsetDateTime::toInstant);
    }

    /** 동기화 대상: 공공서비스 ID가 있는 항목. */
    public List<HelpItem> findSyncTargets() {
        return findAllInDisplayOrder().stream().filter(i -> i.sourceServiceId() != null).toList();
    }

    /**
     * 동기화 값(신청 기한·상세 URL)을 반영한다. 바뀐 칸이 있을 때만 updated_at을 올린다(응답의 version이 매일 바뀌지 않게).
     * 시각은 DB now()를 쓰는 운영 기록이다.
     */
    public void applySync(HelpItem merged) {
        jdbc.sql("""
                        UPDATE help_items
                        SET updated_at = CASE WHEN (url, deadline) IS DISTINCT FROM (:url, :deadline)
                                              THEN now() ELSE updated_at END,
                            url = :url, deadline = :deadline,
                            synced_at = now()
                        WHERE id = :id
                        """)
                .param("id", merged.id())
                .param("url", merged.url())
                .param("deadline", merged.deadline())
                .update();
    }

    public long startSyncLog() {
        return jdbc.sql("INSERT INTO service_sync_log (started_at, status) VALUES (now(), 'RUNNING') RETURNING id")
                .query(Long.class)
                .single();
    }

    public void finishSyncLog(long id, boolean success, int updatedCount, String error) {
        jdbc.sql("""
                        UPDATE service_sync_log
                        SET finished_at = now(), status = :status, updated_count = :count, error = :error
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("status", success ? "SUCCESS" : "FAILED")
                .param("count", updatedCount)
                .param("error", error)
                .update();
    }
}
