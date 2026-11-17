package com.haengunping.common.job;

/** {@link JobRuns#runOnce}의 결과. */
public enum RunOutcome {
    /** 이번 호출이 작업을 실행했고 성공했다. */
    RAN,
    /** 이미 끝났거나 다른 곳에서 실행 중이라 건너뛰었다. */
    SKIPPED,
    /** 이번 호출이 실행했지만 예외로 실패했다. 다음 호출에서 다시 실행된다. */
    FAILED
}
