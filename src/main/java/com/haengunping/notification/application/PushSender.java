package com.haengunping.notification.application;

import com.haengunping.notification.domain.PushMessage;
import com.haengunping.notification.domain.PushSubscription;
import com.haengunping.notification.infra.PushSubscriptionRepository;
import com.haengunping.notification.infra.webpush.WebPushClient;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 한 사용자의 모든 구독(기기)에 알림을 보낸다. 실패는 무시하고(알림은 부가 기능), 만료된 구독은 지운다(NOTI-05).
 * 트랜잭션을 걸지 않는다: 푸시 서비스 응답을 기다리는 동안 DB 연결을 잡고 있지 않게.
 * shortcut: 발송 직전에 엔드포인트 허용 목록을 다시 보지 않는다(구독은 검증하는 API로만 들어온다), DB에 직접 넣는 경로가 생기면 허용 목록을 설정으로 빼고 재확인.
 * shortcut: 기기마다 순차로 보낸다(기기당 최대 3초), 알림 대상이 수백 명을 넘으면 병렬 발송·동시 한도를 둔다.
 */
@Service
public class PushSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    private final PushSubscriptionRepository subscriptions;
    private final WebPushClient client;

    public PushSender(PushSubscriptionRepository subscriptions, WebPushClient client) {
        this.subscriptions = subscriptions;
        this.client = client;
    }

    /** @return 푸시 서비스가 받은(2xx) 기기 수 */
    public int send(long userId, PushMessage message) {
        if (!client.isEnabled()) {
            return 0;
        }
        int delivered = 0;
        for (PushSubscription s : subscriptions.findByUserId(userId)) {
            try {
                int status = client.send(s.endpoint(), s.p256dh(), s.auth(), message.payload(), message.ttl());
                if (status == 404 || status == 410) {
                    subscriptions.deleteById(s.id());
                } else if (status / 100 == 2) {
                    delivered++;
                } else {
                    log.warn("웹 푸시 거절 status={}", status); // endpoint는 기기 식별자라 남기지 않는다
                }
            } catch (IOException e) {
                log.warn("웹 푸시 실패 {}", e.getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return delivered;
            }
        }
        return delivered;
    }
}
