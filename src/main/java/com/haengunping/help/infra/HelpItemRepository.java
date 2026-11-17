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
        return jdbc.sql("SELECT max(synced_at) FROM help_items")
                .query((rs, n) -> rs.getObject(1, OffsetDateTime.class))
                .optional()
                .map(OffsetDateTime::toInstant);
    }
}
