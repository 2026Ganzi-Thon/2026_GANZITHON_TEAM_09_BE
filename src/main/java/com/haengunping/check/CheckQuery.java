package com.haengunping.check;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/**
 * check 모듈이 다른 모듈에 여는 읽기 창구(prd.md 10절). 점검은 민감정보라 답은 저장하지 않고,
 * 여기서도 점수·결과 코드를 내주지 않는다. 필요한 판단(기준 이상인지, 이번 주에 했는지)만 준다.
 */
public interface CheckQuery {

    /**
     * {@code since} 이후에 제출한 점검 결과에 우울(DEPRESSION) 또는 불안(ANXIETY)이 있는지(HELP-06).
     * 닫기만 한 주는 결과가 없으므로 포함하지 않는다.
     */
    boolean aboveThresholdSince(long userId, Instant since);

    /**
     * {@code weekStart}(월요일) 주에 점검을 제출했거나 닫은 사용자(NOTI-03).
     * 점검 알림을 보낼 때 이미 한 사람을 빼는 데 쓴다. {@code userIds}에 없는 사용자는 돌려주지 않는다.
     */
    Set<Long> usersWithRecord(LocalDate weekStart, Collection<Long> userIds);
}
