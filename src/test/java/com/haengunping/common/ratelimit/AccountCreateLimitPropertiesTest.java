package com.haengunping.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** 잘못된 한도 값은 설정 바인딩 단계(= 기동)에서 실패한다. */
class AccountCreateLimitPropertiesTest {

    private static AccountCreateLimitProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("haengunping.rate-limit.account-create", AccountCreateLimitProperties.class);
    }

    @Test
    void 설정이_없으면_기본_10회_1시간() {
        var p = bind(Map.of());
        assertThat(p.limit()).isEqualTo(10);
        assertThat(p.window()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void 값을_주면_그대로_쓴다() {
        var p = bind(Map.of(
                "haengunping.rate-limit.account-create.limit", "200",
                "haengunping.rate-limit.account-create.window", "PT30M"));
        assertThat(p.limit()).isEqualTo(200);
        assertThat(p.window()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void 한도가_0_이하면_실패() {
        assertThatThrownBy(() -> bind(Map.of("haengunping.rate-limit.account-create.limit", "0")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(Map.of("haengunping.rate-limit.account-create.limit", "-5")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 창이_0_이하거나_형식이_틀리면_실패() {
        assertThatThrownBy(() -> bind(Map.of("haengunping.rate-limit.account-create.window", "PT0S")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(Map.of("haengunping.rate-limit.account-create.window", "abc")))
                .isInstanceOf(RuntimeException.class);
    }
}
