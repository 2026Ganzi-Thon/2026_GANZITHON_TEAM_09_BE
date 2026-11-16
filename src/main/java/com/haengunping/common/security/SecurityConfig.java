package com.haengunping.common.security;

import com.haengunping.common.web.ApiPaths;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 공통 보안 설정(architecture 보안 표, SEC-01·04·07·12).
 *
 * <ul>
 *   <li>CSRF: SPA 방식. {@code XSRF-TOKEN} 쿠키(JS가 읽음) → {@code X-XSRF-TOKEN} 헤더로 이중 제출.</li>
 *   <li>세션: 서버 세션을 만들지 않는다. 인증은 account 모듈이 {@code hp_session} 쿠키 필터로 붙인다(담당 A).
 *       그 필터가 생기기 전에는 permitAll이 아닌 API가 모두 401이다.</li>
 *   <li>401·403: 빈 본문 대신 {@link com.haengunping.common.error.GlobalExceptionHandler}로 넘겨
 *       다른 에러와 같은 {@code {code, message}}로 답한다.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            @Value("${haengunping.cookie-secure:true}") boolean cookieSecure) throws Exception {

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.path("/").secure(cookieSecure).sameSite("Lax"));

        http
                .csrf(csrf -> {
                    csrf.spa();
                    csrf.csrfTokenRepository(csrfRepository);
                })
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, ApiPaths.V1 + "/health").permitAll()
                        .requestMatchers(HttpMethod.POST, ApiPaths.V1 + "/accounts").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, e) -> resolver.resolveException(request, response, null, e))
                        .accessDeniedHandler((request, response, e) -> resolver.resolveException(request, response, null, e)));

        return http.build();
    }

    /**
     * 비밀번호 로그인은 없다(D-01). 이 빈이 없으면 Spring Boot가 기본 사용자를 만들고
     * 임의 비밀번호를 로그에 찍는다(2026-11-16 gitleaks 점검에서 발견). 아무도 찾지 못하는 빈으로 막는다.
     */
    @Bean
    UserDetailsService noPasswordUsers() {
        return username -> {
            throw new UsernameNotFoundException("비밀번호 로그인은 없다");
        };
    }
}
