package com.haengunping.common.time;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스의 "지금"을 정하는 유일한 곳(D-08).
 * 날짜 계산은 이 {@link Clock}을 주입받아 {@code LocalDate.now(clock)}처럼 쓴다.
 * 인자 없는 {@code now()} 호출은 ArchitectureTest가 막는다.
 * 시연 시계(demo 모듈)는 이 빈을 대신하는 Clock을 등록한다.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.system(KST);
    }
}
