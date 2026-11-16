
package com.haengunping.account.infra.security;

import com.haengunping.account.application.SessionService;
import com.haengunping.common.security.ApiSecurityCustomizer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class AccountSessionSecurityConfiguration {

    @Bean
    public ApiSecurityCustomizer accountSessionSecurityCustomizer(
            SessionService sessionService) {

        return http -> http.addFilterBefore(
                new SessionAuthenticationFilter(sessionService),
                UsernamePasswordAuthenticationFilter.class
        );
    }
}
