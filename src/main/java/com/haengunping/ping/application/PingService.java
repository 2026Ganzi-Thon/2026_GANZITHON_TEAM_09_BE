package com.haengunping.ping.application;

import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import com.haengunping.group.GroupQuery;
import com.haengunping.mission.MissionQuery;
import com.haengunping.ping.domain.Emoji;
import com.haengunping.ping.domain.Ping;
import com.haengunping.ping.infra.PingRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 핑 보내기·그룹의 오늘 핑(PING-01~04, GRP-14). 그룹 구성원에게 푸시는 보내지 않는다(PING-04). */
@Service
public class PingService {

    private final PingRepository pings;
    private final GroupQuery groups;
    private final MissionQuery missions;
    private final Clock clock;

    public PingService(PingRepository pings, GroupQuery groups, MissionQuery missions, Clock clock) {
        this.pings = pings;
        this.groups = groups;
        this.missions = missions;
        this.clock = clock;
    }

    /** 오늘 미션을 완료했으면 하루 1번. 지금 속한 모든 그룹에 간다(PING-02). */
    @Transactional
    public List<Long> send(long userId, Emoji emoji) {
        LocalDate today = LocalDate.now(clock);
        if (missions.completedUserIds(Set.of(userId), today).isEmpty()) {
            throw new ApiException(ErrorCode.MISSION_NOT_COMPLETED);
        }
        if (pings.insertIfAbsent(userId, today, emoji.name(), clock.instant()) == 0) {
            throw new ApiException(ErrorCode.ALREADY_PINGED);
        }
        return groups.activeGroupIdsOf(userId);
    }

    /**
     * 그룹의 오늘 핑: 이모티콘만, 순서 섞음(PING-03). 보낸 시각에 이 그룹 구성원이었던 사람 것만(PING-02),
     * 내가 차단한 구성원 것은 뺀다(GRP-14). 구성원이 아니면 404.
     */
    @Transactional(readOnly = true)
    public List<Emoji> today(long viewerId, long groupId) {
        if (groups.findActiveMember(groupId, viewerId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        LocalDate today = LocalDate.now(clock);
        // shortcut: 지금 구성원의 핑만 본다. 핑을 보낸 뒤 나간 사람 것은 빠진다, 나간 사람 핑도 남겨야 하면 GroupQuery에 '그날 구성원' 조회를 받는다
        List<Long> members = groups.activeUserIdsOf(groupId, clock.instant());
        List<Emoji> result = new ArrayList<>();
        for (Ping ping : pings.findByDateAndUserIdIn(today, members)) {
            boolean memberWhenSent = groups.activeUserIdsOf(groupId, ping.createdAt()).contains(ping.userId());
            if (memberWhenSent && !groups.isBlocked(groupId, viewerId, ping.userId())) {
                result.add(ping.emoji());
            }
        }
        Collections.shuffle(result);
        return result;
    }
}
