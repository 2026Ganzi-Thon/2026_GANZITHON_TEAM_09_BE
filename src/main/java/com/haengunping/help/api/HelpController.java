package com.haengunping.help.api;

import com.haengunping.common.security.CurrentUser;
import com.haengunping.common.web.ApiPaths;
import com.haengunping.help.application.HelpItemsService;
import com.haengunping.help.application.HelpSignalsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/help")
class HelpController {

    private final HelpItemsService helpItems;

    private final HelpSignalsService signals;

    HelpController(HelpItemsService helpItems, HelpSignalsService signals) {
        this.helpItems = helpItems;
        this.signals = signals;
    }

    /**
     * 도움받을 곳 전체 목록. 인증 없이 열린다(HELP-01). 온보딩 전·오프라인에서도 도움받기 탭이 열려야 하기 때문이다.
     * 캐시는 해도 되지만 쓸 때마다 ETag로 다시 확인한다(HELP-05). ETag는 {@link HelpWebConfig}의 필터가 붙인다.
     */
    @GetMapping("/items")
    ResponseEntity<HelpItemsResponse> items() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(HelpItemsResponse.from(helpItems.all()));
    }

    /**
     * 순서 신호 두 개(HELP-06). permitAll이지만 세션 필터가 유효한 쿠키면 인증을 채우므로 본인 신호만 준다.
     * 쿠키가 없거나 무효면 401이 아니라 둘 다 false로 200(온보딩 전 도움받기 탭). 동의 검사는 없다.
     */
    @GetMapping("/signals")
    ResponseEntity<HelpSignalsResponse> signals() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(HelpSignalsResponse.from(signals.signalsFor(CurrentUser.optionalId())));
    }
}
