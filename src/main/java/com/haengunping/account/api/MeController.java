package com.haengunping.account.api;
import com.haengunping.account.api.dto.*;
import com.haengunping.account.application.AccountService;
import com.haengunping.account.domain.User;
import com.haengunping.account.infra.security.SessionCookieFactory;
import com.haengunping.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/me")
public class MeController {
    private final AccountService accounts;
    private final SessionCookieFactory cookies;
    private final Clock clock;
    public MeController(AccountService accounts,SessionCookieFactory cookies,Clock clock){this.accounts=accounts;this.cookies=cookies;this.clock=clock;}
    private MeResponse response(User u){return new MeResponse(u.getNickname(),u.isAdultConfirmed(),u.hasSensitiveConsent(),u.getCreatedAt(),LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))));}
    @GetMapping public MeResponse me(){return response(accounts.get(CurrentUser.id()));}
    @PatchMapping public MeResponse rename(@RequestBody @Valid UpdateNicknameRequest req){return response(accounts.rename(CurrentUser.id(),req.nickname()));}
    @PutMapping("/consents/sensitive") public ResponseEntity<Void> consent(@RequestBody @Valid SensitiveConsentRequest req){accounts.consent(CurrentUser.id(),req.agreed());return ResponseEntity.noContent().build();}
    @PutMapping("/adult-confirmation") public ResponseEntity<Void> adult(@RequestBody @Valid AdultConfirmationRequest req){accounts.confirmAdult(CurrentUser.id(),req.confirmed());return ResponseEntity.noContent().build();}
    @DeleteMapping("/health-records") public ResponseEntity<Void> eraseHealth(){accounts.eraseHealthRecords(CurrentUser.id());return ResponseEntity.noContent().build();}
    @DeleteMapping public ResponseEntity<Void> delete(){accounts.delete(CurrentUser.id());return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,cookies.expire()).build();}
}
