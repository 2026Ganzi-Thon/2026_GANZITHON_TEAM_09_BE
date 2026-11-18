package com.haengunping.ping.application;

import com.haengunping.account.AccountDeletedEvent;
import com.haengunping.ping.infra.PingRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 계정 삭제(ACC-08) 때 핑을 지운다(erd.md 6절). account의 삭제 트랜잭션 안에서 실행한다(MANDATORY). */
@Component
class PingRecordsCleaner {

    private final PingRepository pings;

    PingRecordsCleaner(PingRepository pings) {
        this.pings = pings;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(AccountDeletedEvent event) {
        pings.deleteByUserId(event.userId());
    }
}
