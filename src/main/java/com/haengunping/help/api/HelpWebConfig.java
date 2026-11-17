package com.haengunping.help.api;

import com.haengunping.common.web.ApiPaths;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * 도움받을 곳 목록에 ETag를 붙이고, {@code If-None-Match}가 같으면 304로 답한다(HELP-05).
 * 응답 본문의 해시로 만들므로 목록이 바뀌면 자동으로 달라진다.
 */
@Configuration(proxyBeanMethods = false)
class HelpWebConfig {

    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> helpItemsEtagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns(ApiPaths.V1 + "/help/items");
        registration.setName("helpItemsEtagFilter");
        return registration;
    }
}
