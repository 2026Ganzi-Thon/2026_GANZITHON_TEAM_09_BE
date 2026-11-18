package com.haengunping.common.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 계정 생성 요청 제한(SEC-08)을 설정으로 뺀 값. 평소에는 IP당 10회/시간이고,
 * 행사장처럼 한 공인 IP를 여럿이 쓰는 곳에서는 환경변수로 올린다(DEMO-05).
 * 0 이하 값은 기동 때 바인딩 오류로 실패한다.
 */
@ConfigurationProperties("haengunping.rate-limit.account-create")
public record AccountCreateLimitProperties(
        @DefaultValue("10") int limit,
        @DefaultValue("PT1H") Duration window) {

    public AccountCreateLimitProperties {
        if (limit <= 0) {
            throw new IllegalArgumentException("haengunping.rate-limit.account-create.limit 는 1 이상이어야 한다: " + limit);
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("haengunping.rate-limit.account-create.window 는 0보다 커야 한다: " + window);
        }
    }
}
