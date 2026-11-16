package com.haengunping.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 CSRF 토큰을 한 번 읽어 응답에 {@code XSRF-TOKEN} 쿠키가 실리게 한다(SEC-04).
 * 토큰은 기본적으로 쓰일 때까지 만들어지지 않아, 첫 {@code POST /accounts} 전에 쿠키가 없을 수 있다.
 * 프론트는 스플래시의 {@code GET /me}(401이어도)로 쿠키를 받은 뒤 가입한다.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
