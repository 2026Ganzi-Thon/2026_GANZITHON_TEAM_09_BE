package com.haengunping.notification.application;

import com.haengunping.check.CheckQuery;
import com.haengunping.common.job.JobName;
import com.haengunping.common.job.JobRuns;
import com.haengunping.common.time.ClockConfig;
import com.haengunping.notification.domain.PushMessage;
import com.haengunping.notification.infra.NotificationSettingRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 점검 알림(NOTI-03): 일요일 18:00(KST), 점검 알림을 켠 사용자 중 이번 주 점검을 제출하지도 닫지도 않은
 * 사람(due=true)에게 1번. 같은 일요일에 두 번 돌아도 {@link JobRuns}가 한 번만 보낸다.
 * shortcut: 18:00에 서버가 꺼져 있었으면 그 주 알림은 건너뛴다(점검 창은 그날 밤까지라 늦은 알림의 의미가 작다).
 */
@Component
public class CheckReminderJob {

    private final NotificationSettingRepository settings;
    private final CheckQuery checks;
    private final PushSender sender;
    private final JobRuns jobs;
    private final Clock clock;

    public CheckReminderJob(NotificationSettingRepository settings, CheckQuery checks, PushSender sender, JobRuns jobs,
                            Clock clock) {
        this.settings = settings;
        this.checks = checks;
        this.sender = sender;
        this.jobs = jobs;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 18 * * SUN", zone = "Asia/Seoul")
    public void run() {
        remindOn(LocalDate.now(clock.withZone(ClockConfig.KST)));
    }

    /** @return 알림을 보내기로 한 사용자 수(이미 보낸 날이면 0) */
    int remindOn(LocalDate sunday) {
        int[] reminded = {0};
        jobs.runOnce(JobName.CHECK_REMINDER, sunday, () -> {
            List<Long> on = settings.findWeeklyCheckReminderOn();
            Set<Long> recorded = checks.usersWithRecord(sunday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), on);
            for (long userId : on) {
                if (!recorded.contains(userId)) {
                    sender.send(userId, PushMessage.WEEKLY_CHECK_REMINDER);
                    reminded[0]++;
                }
            }
        });
        return reminded[0];
    }
}
