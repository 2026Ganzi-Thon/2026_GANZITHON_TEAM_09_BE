package com.haengunping.account.infra.security;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
@Component
public class SessionCookieFactory {
    public String create(String raw){return ResponseCookie.from("hp_session",raw).httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(Duration.ofDays(400)).build().toString();}
    public String expire(){return ResponseCookie.from("hp_session","").httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(Duration.ZERO).build().toString();}
}
