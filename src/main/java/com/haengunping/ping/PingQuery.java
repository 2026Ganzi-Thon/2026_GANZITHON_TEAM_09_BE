package com.haengunping.ping;

import java.time.LocalDate;
import java.util.List;

/** ping 모듈이 다른 모듈에 여는 읽기 창구(prd.md 10절). 핑은 본인 것만 다루므로 항상 userId로 묻는다. */
public interface PingQuery {

    /** from~to(양끝 포함, KST 날짜) 중 내가 핑을 남긴 날짜, 날짜순. 내 기록 '이번 달 내가 남긴 핑'에 쓴다. */
    List<LocalDate> pingDates(long userId, LocalDate from, LocalDate to);
}
