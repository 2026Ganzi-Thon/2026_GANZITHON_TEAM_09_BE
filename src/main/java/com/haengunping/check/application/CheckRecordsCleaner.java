package com.haengunping.check.application;

import com.haengunping.account.AccountDeletedEvent;
import com.haengunping.account.HealthRecordsDeletionRequestedEvent;
import com.haengunping.check.infra.CheckRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 쪽 삭제 요청을 받아 점검 기록을 지운다(erd.md 6절). account가 연 트랜잭션 안에서 실행하므로(MANDATORY)
 * 한 곳이라도 실패하면 삭제 전체가 되돌아가 "지웠다고 답했는데 남아 있는" 상태가 생기지 않는다.
 */
@Component
class CheckRecordsCleaner {

    private final CheckRepository checks;

    CheckRecordsCleaner(CheckRepository checks) {
        this.checks = checks;
    }

    /** 기분·점검 기록 삭제(ACC-07). */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(HealthRecordsDeletionRequestedEvent event) {
        checks.deleteByUserId(event.userId());
    }

    /** 계정 삭제(ACC-08). */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(AccountDeletedEvent event) {
        checks.deleteByUserId(event.userId());
    }
}
