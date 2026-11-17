package com.haengunping.notification.application;

import com.haengunping.account.AccountDeletedEvent;
import com.haengunping.notification.infra.NotificationSettingRepository;
import com.haengunping.notification.infra.PushSubscriptionRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 삭제(ACC-08) 때 푸시 구독과 알림 설정을 지운다(erd.md 6절). 남겨 두면 탈퇴한 사람의 기기로 알림이 간다.
 * account가 발행하는 트랜잭션 안에서 바로 실행한다(MANDATORY).
 */
@Component
class NotificationRecordsCleaner {

    private final PushSubscriptionRepository subscriptions;
    private final NotificationSettingRepository settings;

    NotificationRecordsCleaner(PushSubscriptionRepository subscriptions, NotificationSettingRepository settings) {
        this.subscriptions = subscriptions;
        this.settings = settings;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(AccountDeletedEvent event) {
        subscriptions.deleteByUserId(event.userId());
        settings.deleteByUserId(event.userId());
    }
}
