
package com.haengunping.account.infra.security;

import com.haengunping.account.application.SessionService;
import com.haengunping.common.security.ApiSecurityCustomizer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
public class AccountSessionSecurityConfiguration {

    @Bean
    public ApiSecurityCustomizer accountSessionSecurityCustomizer(
            SessionService sessionService,
            @Value("${haengunping.session-cookie-name}") String cookieName) {

        return http -> http.addFilterBefore(
                new SessionAuthenticationFilter(sessionService, cookieName),
                UsernamePasswordAuthenticationFilter.class
        );
    }
}
