package com.haengunping.help.application;

import com.haengunping.help.domain.HelpItem;
import com.haengunping.help.domain.HelpItemSyncRules;
import com.haengunping.help.domain.PublicServiceInfo;
import com.haengunping.help.infra.HelpItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 공공서비스 API로 도움받을 곳 항목을 갱신한다(HELP-04).
 * {@code source_service_id}가 있는 항목만 대상이다. 항목 하나가 실패해도 나머지는 계속하고,
 * 실패한 항목은 기존 값을 그대로 둔다. 같은 값으로 여러 번 돌려도 결과가 같다(멱등, NFR-04).
 */
@Service
public class ServiceSyncService {

    private static final Logger log = LoggerFactory.getLogger(ServiceSyncService.class);
    private static final int ERROR_MAX = 500;

    private final HelpItemRepository repository;
    private final PublicServiceSource source;

    ServiceSyncService(HelpItemRepository repository, PublicServiceSource source) {
        this.repository = repository;
        this.source = source;
    }

    public boolean isConfigured() {
        return source.isConfigured();
    }

    /** 대상 항목을 모두 갱신해 보고 결과를 {@code service_sync_log}에 남긴다. 예외는 던지지 않는다. */
    public SyncResult sync() {
        long logId = repository.startSyncLog();
        List<HelpItem> targets = repository.findSyncTargets();
        List<String> failures = new ArrayList<>();
        int updated = 0;
        for (HelpItem item : targets) {
            try {
                Optional<PublicServiceInfo> info = source.fetch(item.sourceServiceId());
                if (info.isEmpty()) {
                    failures.add(item.id() + ": 서비스 정보 없음");
                    continue;
                }
                repository.applySync(HelpItemSyncRules.merge(item, info.get()));
                updated++;
            } catch (SyncSourceException e) {
                failures.add(item.id() + ": " + e.getMessage());
            } catch (RuntimeException e) {
                // DB 오류 등. 상세는 로그에만 남기고(스택에 키는 없다), 기록에는 종류만 남긴다.
                log.error("공공서비스 동기화 중 항목 처리 실패: {}", item.id(), e);
                failures.add(item.id() + ": 처리 오류");
            }
        }
        boolean success = failures.isEmpty();
        String error = success ? null : truncate(String.join("; ", failures));
        repository.finishSyncLog(logId, success, updated, error);
        if (!success) {
            log.warn("공공서비스 동기화 일부 실패: 성공 {}건, 실패 {}건", updated, failures.size());
        }
        return new SyncResult(success, updated, failures.size());
    }

    private static String truncate(String text) {
        return text.length() <= ERROR_MAX ? text : text.substring(0, ERROR_MAX);
    }

    public record SyncResult(boolean success, int updatedCount, int failedCount) {
    }
}
