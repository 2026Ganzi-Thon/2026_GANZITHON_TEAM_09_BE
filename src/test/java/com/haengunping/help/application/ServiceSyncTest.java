package com.haengunping.help.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.haengunping.common.job.RunOutcome;
import com.haengunping.help.domain.PublicServiceInfo;
import com.haengunping.support.IntegrationTest;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** HELP-04: 동기화는 기존 값을 안전하게 갱신하고, 실패해도 이전 값을 유지하며, 같은 날 두 번 돌지 않는다. */
class ServiceSyncTest extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 17);
    private static final String VOUCHER_SERVICE = "135200005045";

    @MockitoBean
    PublicServiceSource source;

    @Autowired
    ServiceSyncService syncService;

    @Autowired
    ServiceSyncScheduler scheduler;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void backup() {
        jdbc.sql("DROP TABLE IF EXISTS zz_help_backup").update();
        jdbc.sql("CREATE TABLE zz_help_backup AS SELECT * FROM help_items").update();
        when(source.isConfigured()).thenReturn(true);
    }

    @AfterEach
    void restore() {
        jdbc.sql("""
                UPDATE help_items h SET name = b.name, phone = b.phone, url = b.url, apply_method = b.apply_method,
                       deadline = b.deadline, synced_at = b.synced_at, updated_at = b.updated_at
                FROM zz_help_backup b WHERE h.id = b.id
                """).update();
        jdbc.sql("DROP TABLE zz_help_backup").update();
        jdbc.sql("DELETE FROM service_sync_log").update();
        jdbc.sql("DELETE FROM job_runs").update();
    }

    private int targetCount() {
        return jdbc.sql("SELECT count(*) FROM help_items WHERE source_service_id IS NOT NULL")
                .query(Integer.class).single();
    }

    private Map<String, Object> row(String id) {
        return jdbc.sql("SELECT name, phone, url, apply_method, deadline, synced_at FROM help_items WHERE id = :id")
                .param("id", id).query().singleRow();
    }

    private static Optional<PublicServiceInfo> fresh(String id) {
        return Optional.of(new PublicServiceInfo(id, "새 기한", "https://www.gov.kr/portal/new/" + id));
    }

    @Test
    void 대상_항목만_갱신하고_성공을_기록한다() {
        Map<String, Object> before = row("voucher");
        when(source.fetch(anyString())).thenAnswer(inv -> fresh(inv.getArgument(0)));

        ServiceSyncService.SyncResult result = syncService.sync();

        assertThat(result.success()).isTrue();
        assertThat(result.updatedCount()).isEqualTo(targetCount());
        Map<String, Object> voucher = row("voucher");
        assertThat(voucher.get("deadline")).isEqualTo("새 기한");
        assertThat(voucher.get("url")).isEqualTo("https://www.gov.kr/portal/new/" + VOUCHER_SERVICE);
        // 이름·신청 방법·전화는 시드 그대로(2026-11-18 결정)
        assertThat(voucher.get("name")).isEqualTo(before.get("name"));
        assertThat(voucher.get("apply_method")).isEqualTo(before.get("apply_method"));
        assertThat(voucher.get("phone")).isEqualTo(before.get("phone"));
        assertThat(voucher.get("synced_at")).isNotNull();
        // 서비스 ID가 없는 항목(24시간 109 전화, 서울 구 센터)은 손대지 않는다
        assertThat(row("line-109").get("name")).isEqualTo("자살예방상담전화 109");
        assertThat(row("line-109").get("synced_at")).isNull();
        assertThat(jdbc.sql("SELECT status FROM service_sync_log").query(String.class).single()).isEqualTo("SUCCESS");
        assertThat(jdbc.sql("SELECT max(finished_at)::text FROM service_sync_log").query(String.class).single())
                .isNotNull();
    }

    @Test
    void 한_항목이_실패해도_나머지는_갱신하고_실패한_항목은_기존_값을_유지한다() {
        Map<String, Object> before = row("voucher");
        when(source.fetch(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            if (id.equals(VOUCHER_SERVICE)) {
                throw new SyncSourceException("HTTP 500");
            }
            return fresh(id);
        });

        ServiceSyncService.SyncResult result = syncService.sync();

        assertThat(result.success()).isFalse();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(targetCount() - 1);
        assertThat(row("voucher")).isEqualTo(before);
        assertThat(row("mental-center").get("deadline")).isEqualTo("새 기한");
        Map<String, Object> log = jdbc.sql("SELECT status, error FROM service_sync_log").query().singleRow();
        assertThat(log.get("status")).isEqualTo("FAILED");
        assertThat((String) log.get("error")).contains("voucher").contains("HTTP 500");
    }

    @Test
    void 서비스_정보가_없으면_그_항목은_기존_값을_유지하고_실패로_센다() {
        Map<String, Object> before = row("voucher");
        when(source.fetch(anyString())).thenAnswer(inv ->
                inv.getArgument(0).equals(VOUCHER_SERVICE) ? Optional.empty() : fresh(inv.getArgument(0)));

        ServiceSyncService.SyncResult result = syncService.sync();

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(row("voucher")).isEqualTo(before);
    }

    @Test
    void 실패한_동기화는_syncedAt을_앞으로_옮기지_않는다() {
        when(source.fetch(anyString())).thenThrow(new SyncSourceException("통신 실패"));

        syncService.sync();

        assertThat(jdbc.sql("SELECT count(*) FROM service_sync_log WHERE status = 'SUCCESS'")
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM help_items WHERE synced_at IS NOT NULL")
                .query(Integer.class).single()).isZero();
    }

    @Test
    void 같은_값을_다시_받으면_version이_바뀌지_않는다() {
        when(source.fetch(anyString())).thenAnswer(inv -> fresh(inv.getArgument(0)));
        syncService.sync();
        String first = jdbc.sql("SELECT max(updated_at)::text FROM help_items").query(String.class).single();

        syncService.sync();

        assertThat(jdbc.sql("SELECT max(updated_at)::text FROM help_items").query(String.class).single())
                .isEqualTo(first);
    }

    @Test
    void 같은_날짜는_한_번만_실행한다() {
        when(source.fetch(anyString())).thenAnswer(inv -> fresh(inv.getArgument(0)));

        assertThat(scheduler.runFor(DAY)).isEqualTo(RunOutcome.RAN);
        assertThat(scheduler.runFor(DAY)).isEqualTo(RunOutcome.SKIPPED);

        verify(source, times(targetCount())).fetch(anyString());
        assertThat(jdbc.sql("SELECT count(*) FROM service_sync_log").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void 일부_실패한_날은_FAILED로_남고_다음_호출에서_다시_시도한다() {
        when(source.fetch(anyString())).thenThrow(new SyncSourceException("HTTP 503"));

        assertThat(scheduler.runFor(DAY)).isEqualTo(RunOutcome.FAILED);

        doAnswer(inv -> fresh(inv.getArgument(0))).when(source).fetch(anyString());
        assertThat(scheduler.runFor(DAY)).isEqualTo(RunOutcome.RAN);
    }

    @Test
    void API_키가_없으면_건너뛰고_날짜를_소비하지_않는다() {
        when(source.isConfigured()).thenReturn(false);

        assertThat(scheduler.runFor(DAY)).isEqualTo(RunOutcome.SKIPPED);

        verify(source, times(0)).fetch(anyString());
        assertThat(jdbc.sql("SELECT count(*) FROM job_runs").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM service_sync_log").query(Integer.class).single()).isZero();
    }
}
