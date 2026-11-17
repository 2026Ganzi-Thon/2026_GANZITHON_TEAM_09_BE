package com.haengunping.mission.api;

import com.haengunping.common.security.CurrentUser;
import com.haengunping.common.web.ApiPaths;
import com.haengunping.mission.application.MissionService;
import com.haengunping.mission.application.MissionService.Record;
import java.time.YearMonth;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 기록: 그달 참여한 미션(api.md 5절). */
@RestController
public class MyRecordsController {

    private final MissionService service;

    public MyRecordsController(MissionService service) {
        this.service = service;
    }

    @GetMapping(ApiPaths.V1 + "/me/records")
    public RecordsResponse records(@RequestParam YearMonth month) {
        return new RecordsResponse(service.records(CurrentUser.id(), month));
    }

    public record RecordsResponse(List<Record> missions) {
    }
}
