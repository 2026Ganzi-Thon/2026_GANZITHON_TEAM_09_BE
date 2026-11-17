package com.haengunping.mood.domain;

import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 기분 기록 조회 기간. 양 끝 포함 최대 93일(MOOD-05). */
public record MoodPeriod(LocalDate from, LocalDate to) {

    public static final int MAX_DAYS = 93;

    public MoodPeriod {
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "시작 날짜가 끝 날짜보다 늦어요");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "기간은 " + MAX_DAYS + "일까지 볼 수 있어요");
        }
    }
}
