package com.haengunping.notification.infra.webpush;

import java.security.GeneralSecurityException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class WebPushConfig {

    private static final Logger log = LoggerFactory.getLogger(WebPushConfig.class);

    /** 키가 비어 있으면 알림을 끈 채로 뜬다(로컬·CI). 키가 있는데 형식이 틀리면 시작하지 않는다. */
    @Bean
    WebPushClient webPushClient(
            @Value("${haengunping.vapid.public-key:}") String publicKey,
            @Value("${haengunping.vapid.private-key:}") String privateKey,
            @Value("${haengunping.vapid.subject:}") String subject,
            Clock clock) throws GeneralSecurityException {
        if (publicKey.isBlank() || privateKey.isBlank()) {
            log.warn("VAPID 키가 없어 웹 푸시를 보내지 않는다");
            return WebPushClient.disabled();
        }
        return new WebPushClient(new Vapid(publicKey, privateKey, subject), clock);
    }
}
