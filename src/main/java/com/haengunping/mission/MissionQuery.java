package com.haengunping.mission;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/** mission 모듈이 다른 모듈에 여는 읽기 창구(D-03). */
public interface MissionQuery {

    /** userIds 중 그날(KST) 미션을 완료한 사용자. */
    Set<Long> completedUserIds(Collection<Long> userIds, LocalDate date);
}
