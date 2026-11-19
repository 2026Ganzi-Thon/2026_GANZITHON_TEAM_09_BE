package com.haengunping.account.api;
import com.haengunping.account.api.dto.*;
import com.haengunping.account.application.AccountService;
import com.haengunping.account.infra.security.SessionCookieFactory;
import com.haengunping.common.ratelimit.AccountCreateLimitProperties;
import com.haengunping.common.ratelimit.FixedWindowRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accounts;
    private final SessionCookieFactory cookies;
    private final FixedWindowRateLimiter limiter;
    private final AccountCreateLimitProperties createLimit;
    public AccountController(AccountService accounts,SessionCookieFactory cookies,FixedWindowRateLimiter limiter,AccountCreateLimitProperties createLimit){this.accounts=accounts;this.cookies=cookies;this.limiter=limiter;this.createLimit=createLimit;}
    @PostMapping public ResponseEntity<AccountResponse> create(@RequestBody @Valid CreateAccountRequest request,HttpServletRequest servlet){
        limiter.check("account:"+servlet.getRemoteAddr(),createLimit.limit(),createLimit.window());
        var result=accounts.create(request.nickname());
        var user=result.user();
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.SET_COOKIE,cookies.create(result.token()))
                .body(new AccountResponse(user.getNickname(),user.isAdultConfirmed(),user.hasSensitiveConsent()));
    }
}
