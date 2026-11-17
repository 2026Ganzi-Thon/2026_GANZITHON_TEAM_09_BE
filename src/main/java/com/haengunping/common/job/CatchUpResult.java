package com.haengunping.common.job;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link JobRuns#runPending}의 결과.
 *
 * @param ran       이번에 실행해 성공한 날짜(오래된 순)
 * @param stoppedAt 실패했거나 다른 곳에서 실행 중이라 멈춘 날짜. 모두 끝났으면 null
 */
public record CatchUpResult(List<LocalDate> ran, LocalDate stoppedAt) {

    public CatchUpResult {
        ran = List.copyOf(ran);
    }

    public boolean completed() {
        return stoppedAt == null;
    }
}
