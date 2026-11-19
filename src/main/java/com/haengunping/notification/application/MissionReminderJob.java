package com.haengunping.notification.application;

import com.haengunping.mission.MissionQuery;
import com.haengunping.notification.domain.PushMessage;
import com.haengunping.notification.infra.NotificationSettingRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 미션 알림(NOTI-02): 매 30분, 알림 시각이 지금인 사용자 중 오늘 미션을 완료하지 않은 사람에게 1번.
 * shortcut: 서버가 꺼져 있던 30분 칸은 건너뛴다(밀린 알림은 늦게 오면 의미가 없다), 필요하면 job_runs로 따라잡기.
 */
@Component
public class MissionReminderJob {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationSettingRepository settings;
    private final MissionQuery missions;
    private final PushSender sender;
    private final TransactionTemplate tx;
    private final Clock clock;

    public MissionReminderJob(NotificationSettingRepository settings, MissionQuery missions, PushSender sender,
                              TransactionTemplate tx, Clock clock) {
        this.settings = settings;
        this.missions = missions;
        this.sender = sender;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0,30 * * * *", zone = "Asia/Seoul")
    public void run() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(KST));
        remindAt(now.toLocalDate(), LocalTime.of(now.getHour(), now.getMinute() < 30 ? 0 : 30));
    }

    /** @return 알림을 보내기로 한 사용자 수 */
    int remindAt(LocalDate today, LocalTime slot) {
        List<Long> due = settings.findDueForMissionReminder(slot, today);
        Set<Long> completed = missions.completedUserIds(due, today);
        int reminded = 0;
        for (long userId : due) {
            if (completed.contains(userId)) {
                continue;
            }
            // 보낼 차례를 먼저 짧은 트랜잭션으로 잡고, 발송(최대 몇 초)은 트랜잭션 밖에서 한다
            Integer claimed = tx.execute(s -> settings.claimMissionReminder(userId, today));
            if (claimed != null && claimed == 1) {
                sender.send(userId, PushMessage.MISSION_REMINDER);
                reminded++;
            }
        }
        return reminded;
    }
}
