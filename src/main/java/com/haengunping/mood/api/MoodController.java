package com.haengunping.mood.api;

import com.haengunping.account.AccountQuery;
import com.haengunping.common.security.CurrentUser;
import com.haengunping.common.web.ApiPaths;
import com.haengunping.mood.Mood;
import com.haengunping.mood.application.MoodService;
import com.haengunping.mood.domain.DailyMood;
import com.haengunping.mood.domain.MoodPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기분 저장·조회(api.md 4절, MOOD-01·02·05). 기분 값은 민감정보라 로그에 남기지 않는다(SEC-11). */
@RestController
@RequestMapping(ApiPaths.V1 + "/moods")
public class MoodController {

    private final MoodService service;
    private final AccountQuery accounts;

    public MoodController(MoodService service, AccountQuery accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @PutMapping("/today")
    public MoodResponse saveToday(@Valid @RequestBody SaveMoodRequest request) {
        long me = CurrentUser.id();
        accounts.requireSensitiveConsent(me); // ACC-05
        return MoodResponse.of(service.saveToday(me, request.mood()));
    }

    @GetMapping
    public MoodsResponse list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new MoodsResponse(service.list(CurrentUser.id(), new MoodPeriod(from, to)).stream()
                .map(MoodResponse::of)
                .toList());
    }

    public record SaveMoodRequest(@NotNull Mood mood) {
    }

    public record MoodResponse(LocalDate date, Mood mood) {
        static MoodResponse of(DailyMood m) {
            return new MoodResponse(m.date(), m.mood());
        }
    }

    public record MoodsResponse(List<MoodResponse> moods) {
    }
}
