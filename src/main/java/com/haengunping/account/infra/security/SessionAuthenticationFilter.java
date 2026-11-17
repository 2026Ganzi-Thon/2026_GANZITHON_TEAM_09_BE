package com.haengunping.account.infra.security;
import com.haengunping.account.application.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.web.filter.OncePerRequestFilter;
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final SessionService sessions;
    private final String cookieName;
    public SessionAuthenticationFilter(SessionService sessions, String cookieName){
        this.sessions=sessions;
        this.cookieName = cookieName;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        Cookie[] cookies=request.getCookies();
        if(cookies!=null)for(Cookie cookie:cookies){
            if (cookieName.equals(cookie.getName())) {
                sessions.authenticate(cookie.getValue()).ifPresent(userId->{
                    var auth=new UsernamePasswordAuthenticationToken(userId,null,List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
                break;
            }
        }
        try{chain.doFilter(request,response);} finally{SecurityContextHolder.clearContext();}
    }
}
