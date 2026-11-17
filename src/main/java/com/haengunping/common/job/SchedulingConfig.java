package com.haengunping.common.job;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 작업을 켠다(docs/architecture.md 3절). 모듈의 예약 작업은 {@code zone = "Asia/Seoul"}을 직접 적는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
