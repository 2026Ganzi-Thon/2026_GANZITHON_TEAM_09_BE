package com.haengunping.ping.api;

import com.haengunping.common.ratelimit.FixedWindowRateLimiter;
import com.haengunping.common.security.CurrentUser;
import com.haengunping.common.web.ApiPaths;
import com.haengunping.ping.application.PingService;
import com.haengunping.ping.domain.Emoji;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** api.md 6절. */
@RestController
public class PingController {

    private final PingService service;
    private final FixedWindowRateLimiter limiter;
    private final Clock clock;

    public PingController(PingService service, FixedWindowRateLimiter limiter, Clock clock) {
        this.service = service;
        this.limiter = limiter;
        this.clock = clock;
    }

    @PostMapping(ApiPaths.V1 + "/pings")
    @ResponseStatus(HttpStatus.CREATED)
    public SendResponse send(@RequestBody @Valid SendRequest request) {
        long userId = CurrentUser.id();
        limiter.check("ping:" + userId, 30, Duration.ofHours(1)); // SEC-08: 하루 1번이지만 409 반복 호출을 막는다
        return new SendResponse(service.send(userId, request.emoji()));
    }

    @GetMapping(ApiPaths.V1 + "/groups/{groupId}/pings")
    public TodayResponse today(@PathVariable long groupId) {
        List<EmojiView> pings = service.today(CurrentUser.id(), groupId).stream().map(EmojiView::new).toList();
        return new TodayResponse(LocalDate.now(clock), pings);
    }

    public record SendRequest(@NotNull Emoji emoji) {
    }

    public record SendResponse(List<Long> groupIds) {
    }

    public record EmojiView(Emoji emoji) {
    }

    public record TodayResponse(LocalDate date, List<EmojiView> pings) {
    }
}
