package com.haengunping.mood.application;

import com.haengunping.account.AccountDeletedEvent;
import com.haengunping.account.HealthRecordsDeletionRequestedEvent;
import com.haengunping.mood.infra.HelpCardDismissalRepository;
import com.haengunping.mood.infra.MoodRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 쪽 삭제 요청을 받아 mood 모듈의 기록을 지운다(erd.md 6절).
 * account가 발행하는 트랜잭션 안에서 바로 실행한다(MANDATORY). 한 곳이라도 실패하면 삭제 전체가 되돌아가
 * "지웠다고 답했는데 남아 있는" 상태가 생기지 않는다.
 */
@Component
class MoodRecordsCleaner {

    private final MoodRepository moods;
    private final HelpCardDismissalRepository dismissals;

    MoodRecordsCleaner(MoodRepository moods, HelpCardDismissalRepository dismissals) {
        this.moods = moods;
        this.dismissals = dismissals;
    }

    /** 기분·점검 기록 삭제(ACC-07): 기분만. 도움 카드 닫은 시각은 건강 기록이 아니라 남긴다. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(HealthRecordsDeletionRequestedEvent event) {
        moods.deleteByUserId(event.userId());
    }

    /** 계정 삭제(ACC-08). */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(AccountDeletedEvent event) {
        moods.deleteByUserId(event.userId());
        dismissals.deleteByUserId(event.userId());
    }
}
