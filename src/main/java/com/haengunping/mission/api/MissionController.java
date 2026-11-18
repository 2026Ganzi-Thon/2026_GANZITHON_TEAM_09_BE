package com.haengunping.mission.api;

import com.haengunping.common.security.CurrentUser;
import com.haengunping.common.web.ApiPaths;
import com.haengunping.mission.application.GroupTodayService;
import com.haengunping.mission.application.GroupTodayService.GroupToday;
import com.haengunping.mission.application.MissionService;
import com.haengunping.mission.application.MissionService.Defaults;
import com.haengunping.mission.application.MissionService.TodayMission;
import com.haengunping.mission.domain.DailyMission;
import com.haengunping.mission.domain.DailyMission.Place;
import com.haengunping.mission.domain.DailyMission.Status;
import com.haengunping.mission.domain.MissionTopic;
import com.haengunping.mission.domain.MissionTopic.Category;
import com.haengunping.mission.domain.MissionTopic.SizeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 오늘 미션 조회·시작(api.md 5절, MIS-02~06). */
@RestController
@RequestMapping(ApiPaths.V1 + "/missions/today")
public class MissionController {

    private final MissionService service;
    private final GroupTodayService groupToday;

    public MissionController(MissionService service, GroupTodayService groupToday) {
        this.service = service;
        this.groupToday = groupToday;
    }

    @GetMapping
    public TodayMissionResponse today() {
        return TodayMissionResponse.from(service.today(CurrentUser.id()));
    }

    @PostMapping("/start")
    public TodayMissionResponse start(@Valid @RequestBody StartRequest request) {
        return TodayMissionResponse.from(service.start(CurrentUser.id(), request.minutes(), request.place(), request.size()));
    }

    @PostMapping("/complete")
    public CompleteResponse complete() {
        long userId = CurrentUser.id();
        Instant completedAt = service.complete(userId);
        return new CompleteResponse(completedAt, groupToday.afterCompletion(userId));
    }

    public record CompleteResponse(Instant completedAt, List<GroupToday> groups) {
    }

    /** 값의 범위(5·10·20분, 크기)는 도메인이 검사해 400을 준다. */
    public record StartRequest(@NotNull Integer minutes, @NotNull Place place, @NotNull Integer size) {
    }

    public record TodayMissionResponse(
            LocalDate date,
            Topic topic,
            Defaults defaults,
            Map<Integer, Integer> sizeByMinutes,
            Status status,
            Instant startedAt,
            Instant completedAt,
            Integer minutes,
            Place place,
            Integer size) {

        static TodayMissionResponse from(TodayMission today) {
            DailyMission m = today.mission();
            return new TodayMissionResponse(m.date(), Topic.from(today.topic()), today.defaults(), today.sizeByMinutes(),
                    m.status(), m.startedAt(), m.completedAt(), m.minutes(), m.place(), m.size());
        }
    }

    public record Topic(short topicId, Category category, String outdoorText, String indoorText, SizeType sizeType) {
        static Topic from(MissionTopic t) {
            return new Topic(t.id(), t.category(), t.outdoorText(), t.indoorText(), t.sizeType());
        }
    }
}
