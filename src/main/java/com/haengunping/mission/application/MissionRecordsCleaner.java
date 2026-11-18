package com.haengunping.mission.application;

import com.haengunping.account.AccountDeletedEvent;
import com.haengunping.mission.infra.DailyMissionRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 삭제(ACC-08) 때 미션 기록을 지운다(erd.md 6절). 기록 삭제(ACC-07)는 기분·점검만이라 미션은 남긴다.
 * account가 발행하는 트랜잭션 안에서 바로 실행한다(MANDATORY). 한 곳이라도 실패하면 삭제 전체가 되돌아간다.
 */
@Component
class MissionRecordsCleaner {

    private final DailyMissionRepository missions;

    MissionRecordsCleaner(DailyMissionRepository missions) {
        this.missions = missions;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(AccountDeletedEvent event) {
        missions.deleteByUserId(event.userId());
    }
}
