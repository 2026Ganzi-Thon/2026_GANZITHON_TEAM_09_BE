package com.haengunping.common.clock;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods=false)
public class FallbackClockConfiguration {
    @Bean @ConditionalOnMissingBean(Clock.class)
    Clock applicationClock() { return Clock.systemUTC(); }
}
