package com.haengunping.check.api;

import com.haengunping.account.AccountQuery;
import com.haengunping.check.application.CheckService;
import com.haengunping.check.domain.CheckResult;
import com.haengunping.check.domain.CheckRules;
import com.haengunping.check.domain.CheckSection;
import com.haengunping.common.error.FieldValidationException;
import com.haengunping.common.security.CurrentUser;
import com.haengunping.common.web.ApiPaths;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주간 점검 API(api.md 7절, CHK-01~06). 점검 답과 결과는 민감정보라 로그에 남기지 않는다(SEC-11).
 * 응답에는 점수가 없다(CHK-04).
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/checks")
public class CheckController {

    private final CheckService service;
    private final AccountQuery accounts;

    public CheckController(CheckService service, AccountQuery accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    /** 동의 없이도 볼 수 있다. 어디까지 허용할지는 제출에서 가린다. */
    @GetMapping("/current")
    public CurrentResponse current() {
        CheckService.Current c = service.current(CurrentUser.id());
        return new CurrentResponse(c.weekStart(), c.due(), c.done(), c.dismissed());
    }

    /**
     * 검사 순서: 동의(403) → 답 형식(400) → 점검 기간·중복(409). 프론트 목업과 같다.
     * {@code @Valid}를 쓰면 동의보다 앞서 400이 나가므로 형식은 직접 검사한다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitResponse submit(@RequestBody SubmitRequest request) {
        long me = CurrentUser.id();
        accounts.requireSensitiveConsent(me); // ACC-05
        List<String> invalid = CheckRules.invalidFields(request.answers());
        if (!invalid.isEmpty()) {
            throw new FieldValidationException(invalid);
        }
        return SubmitResponse.of(service.submit(me, request.answers()));
    }

    /** 이번 주 점검 닫기. 동의·창과 무관하게 204, 이미 기록이 있으면 그대로 둔다(멱등). */
    @PostMapping("/current/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss() {
        service.dismiss(CurrentUser.id());
    }

    /** 순서는 우울1, 우울2, 불안1, 불안2. */
    public record SubmitRequest(List<Integer> answers) {
    }

    public record CurrentResponse(LocalDate weekStart, boolean due, boolean done, boolean dismissed) {
    }

    /** 결과 코드·문장·보여줄 구역만. 점수 칸을 더하지 않는다(CHK-04, 테스트가 키 집합을 고정한다). */
    public record SubmitResponse(
            List<CheckResult> results, List<String> messages, String notice, List<CheckSection> sections) {

        static SubmitResponse of(List<CheckResult> results) {
            return new SubmitResponse(
                    results,
                    results.stream().map(CheckResult::message).toList(),
                    CheckRules.NOTICE,
                    CheckRules.sections(results));
        }
    }
}
