package com.haengunping.mood.api;

import com.haengunping.common.security.CurrentUser;
import com.haengunping.common.web.ApiPaths;
import com.haengunping.mood.application.HelpCardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 가벼운 도움 카드(api.md 4절, MOOD-04). */
@RestController
@RequestMapping(ApiPaths.V1 + "/help-card")
public class HelpCardController {

    private final HelpCardService service;

    public HelpCardController(HelpCardService service) {
        this.service = service;
    }

    @GetMapping
    public HelpCardResponse helpCard() {
        return service.shouldShow(CurrentUser.id()) ? HelpCardResponse.LOW_MOOD : HelpCardResponse.HIDDEN;
    }

    @PostMapping("/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss() {
        service.dismiss(CurrentUser.id());
    }

    /** reason은 show=false면 null. */
    public record HelpCardResponse(boolean show, String reason) {
        static final HelpCardResponse LOW_MOOD = new HelpCardResponse(true, "LOW_MOOD_3_DAYS");
        static final HelpCardResponse HIDDEN = new HelpCardResponse(false, null);
    }
}
