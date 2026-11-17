package com.haengunping.common.job;

/**
 * 날짜 단위로 한 번만 도는 예약 작업(docs/architecture.md 예약 작업 표).
 * 이름은 {@code job_runs.job_name}에 그대로 저장되므로 바꾸지 않는다. 새 작업은 여기 더하고 C가 리뷰한다.
 */
public enum JobName {
    /** 00:00 전날 그룹별 확정 성장 계산(character, 담당 B). CHR-03 */
    FINALIZE_PROGRESS,
    /** 00:10 온라인 그룹 7일 미접속 구성원 내보내기(group, 담당 A). GRP-12 */
    REMOVE_INACTIVE,
    /** 04:00 공공서비스 API 동기화(help, 담당 C). HELP-04 */
    SYNC_SERVICES
}
