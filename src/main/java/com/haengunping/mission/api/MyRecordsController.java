package com.haengunping.mission.api;

import com.haengunping.common.security.CurrentUser;
import com.haengunping.common.web.ApiPaths;
import com.haengunping.mission.application.MissionService;
import com.haengunping.mission.application.MissionService.Record;
import com.haengunping.ping.PingQuery;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 기록: 그달 참여한 미션과 핑을 남긴 날짜(api.md 5절). */
@RestController
public class MyRecordsController {

    private final MissionService service;
    private final PingQuery pings;

    public MyRecordsController(MissionService service, PingQuery pings) {
        this.service = service;
        this.pings = pings;
    }

    @GetMapping(ApiPaths.V1 + "/me/records")
    public RecordsResponse records(@RequestParam YearMonth month) {
        long me = CurrentUser.id();
        return new RecordsResponse(
                service.records(me, month),
                pings.pingDates(me, month.atDay(1), month.atEndOfMonth()));
    }

    /** pings: 그달 내가 핑을 남긴 날짜(KST), 날짜순. 이모티콘·그룹은 넣지 않는다. */
    public record RecordsResponse(List<Record> missions, List<LocalDate> pings) {
    }
}
