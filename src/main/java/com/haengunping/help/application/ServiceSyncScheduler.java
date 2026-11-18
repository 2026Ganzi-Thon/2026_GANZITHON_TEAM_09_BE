package com.haengunping.help.application;

import com.haengunping.common.job.JobName;
import com.haengunping.common.job.JobRuns;
import com.haengunping.common.job.RunOutcome;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 04:00(KST) 공공서비스 동기화(docs/architecture.md 3절). 같은 날짜는 {@link JobRuns}로 한 번만 돈다. */
@Component
public class ServiceSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ServiceSyncScheduler.class);

    private final ServiceSyncService syncService;
    private final JobRuns jobRuns;
    private final Clock clock;

    ServiceSyncScheduler(ServiceSyncService syncService, JobRuns jobRuns, Clock clock) {
        this.syncService = syncService;
        this.jobRuns = jobRuns;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        runFor(LocalDate.now(clock));
    }

    /** API 키가 없으면 날짜를 소비하지 않고 건너뛴다(키를 넣으면 같은 날 다음 실행이 돈다). */
    RunOutcome runFor(LocalDate day) {
        if (!syncService.isConfigured()) {
            log.info("DATA_GO_KR_KEY가 없어 공공서비스 동기화를 건너뜀");
            return RunOutcome.SKIPPED;
        }
        return jobRuns.runOnce(JobName.SYNC_SERVICES, day, () -> {
            ServiceSyncService.SyncResult result = syncService.sync();
            if (!result.success()) {
                // 일부 실패: 갱신된 항목은 이미 반영됐다. FAILED로 남겨 다음 실행에서 다시 시도한다.
                throw new IllegalStateException("공공서비스 동기화 일부 실패 " + result.failedCount() + "건");
            }
        });
    }
}
