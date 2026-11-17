package com.haengunping.account.infra.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieFactory {

    private final String cookieName;
    private final boolean cookieSecure;

    public SessionCookieFactory(
            @Value("${haengunping.session-cookie-name}") String cookieName,
            @Value("${haengunping.cookie-secure:true}") boolean cookieSecure) {
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
    }

    public String create(String raw) {
        return ResponseCookie.from(cookieName, raw)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(400))
                .build()
                .toString();
    }

    public String expire() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build()
                .toString();
    }
}