package com.haengunping.mood;

import java.time.LocalDate;
import java.util.Optional;

/** mood 모듈이 다른 모듈에 여는 읽기 창구(prd.md 10절). 기분은 본인 것만 다루므로 항상 userId로 묻는다. */
public interface MoodQuery {

    /** 그날(KST) 기분. 입력하지 않은 날은 비어 있다. */
    Optional<Mood> moodOn(long userId, LocalDate date);

    /** weekStart(월요일)부터 일요일까지 7일 중 낮은 기분을 기록한 날 수(CHK-03). */
    int lowMoodDaysInWeek(long userId, LocalDate weekStart);
}
