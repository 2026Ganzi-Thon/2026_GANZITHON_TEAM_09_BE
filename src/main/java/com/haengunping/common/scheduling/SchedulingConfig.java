package com.haengunping.common.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 모듈의 @Scheduled 작업(알림, 00:00 확정 등)을 켠다. 서버 1대 가정(architecture). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
