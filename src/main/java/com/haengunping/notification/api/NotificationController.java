package com.haengunping.notification.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.haengunping.common.security.CurrentUser;
import com.haengunping.common.web.ApiPaths;
import com.haengunping.notification.application.NotificationService;
import com.haengunping.notification.application.NotificationService.Settings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 웹 푸시 구독과 알림 설정(api.md 9절, NOTI-01~03). */
@RestController
@RequestMapping(ApiPaths.V1 + "/me")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping("/push-subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public void subscribe(@Valid @RequestBody SubscribeRequest request) {
        service.subscribe(CurrentUser.id(), request.endpoint(), request.keys().p256dh(), request.keys().auth());
    }

    @DeleteMapping("/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        service.unsubscribe(CurrentUser.id(), request.endpoint());
    }

    @GetMapping("/notification-settings")
    public SettingsResponse settings() {
        return SettingsResponse.from(service.settings(CurrentUser.id()));
    }

    @PutMapping("/notification-settings")
    public SettingsResponse updateSettings(@Valid @RequestBody SettingsRequest request) {
        return SettingsResponse.from(
                service.updateSettings(CurrentUser.id(), request.missionReminderTime(), request.weeklyCheckReminder()));
    }

    public record SubscribeRequest(@NotNull String endpoint, @NotNull @Valid Keys keys) {
        public record Keys(@NotNull String p256dh, @NotNull String auth) {
        }
    }

    public record UnsubscribeRequest(@NotNull String endpoint) {
    }

    /** missionReminderTime은 null이면 미션 알림 끔. */
    public record SettingsRequest(LocalTime missionReminderTime, @NotNull Boolean weeklyCheckReminder) {
    }

    /** 시각은 api.md대로 HH:mm(기본 직렬화는 HH:mm:ss). */
    public record SettingsResponse(@JsonFormat(pattern = "HH:mm") LocalTime missionReminderTime, boolean weeklyCheckReminder) {
        static SettingsResponse from(Settings settings) {
            return new SettingsResponse(settings.missionReminderTime(), settings.weeklyCheckReminder());
        }
    }
}
