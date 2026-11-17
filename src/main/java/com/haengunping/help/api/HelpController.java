package com.haengunping.help.api;

import com.haengunping.common.web.ApiPaths;
import com.haengunping.help.application.HelpItemsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/help")
class HelpController {

    private final HelpItemsService helpItems;

    HelpController(HelpItemsService helpItems) {
        this.helpItems = helpItems;
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
}
