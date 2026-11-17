package com.haengunping.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.haengunping.support.IntegrationTest;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * 앱 전체가 KST 시계 하나만 쓰는지 확인한다(D-08).
 * 2026-11-18: UTC 대체 시계가 먼저 등록돼 KST 시계가 빠진 적이 있다. 그러면 KST 00~09시에 '오늘'이 어제가 된다.
 */
class ClockConfigTest extends IntegrationTest {

    @Autowired
    ApplicationContext context;

    @Test
    void D08_앱의_시계는_하나이고_KST다() {
        Map<String, Clock> clocks = context.getBeansOfType(Clock.class);

        assertThat(clocks).hasSize(1);
        assertThat(clocks.values().iterator().next().getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
