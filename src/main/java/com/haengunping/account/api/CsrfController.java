package com.haengunping.account.api;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/** 최초 계정 생성 전부터 CSRF 쿠키를 발급하기 위한 공개 GET. */
@RestController @RequestMapping("/api/v1")
public class CsrfController {
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken());}
}
