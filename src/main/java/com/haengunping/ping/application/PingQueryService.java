package com.haengunping.ping.application;

import com.haengunping.ping.PingQuery;
import com.haengunping.ping.infra.PingRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PingQuery} 구현. PingService는 MissionQuery를 쓰므로, mission 모듈이 이 창구를 쓸 때
 * 빈 생성이 서로 물리지 않게 따로 둔다.
 */
@Service
class PingQueryService implements PingQuery {

    private final PingRepository pings;

    PingQueryService(PingRepository pings) {
        this.pings = pings;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalDate> pingDates(long userId, LocalDate from, LocalDate to) {
        return pings.findDates(userId, from, to);
    }
}
