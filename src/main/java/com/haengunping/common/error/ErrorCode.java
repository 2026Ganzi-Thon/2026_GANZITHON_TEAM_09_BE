package com.haengunping.common.error;

import org.springframework.http.HttpStatus;

/**
 * 응답 에러 코드. docs/api.md 1절 에러 표와 1:1로 맞춘다.
 * 코드를 더하면 api.md를 같은 PR에서 고치고 프론트에 알린다.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "다시 시작해 주세요"),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "요청이 만료됐어요. 다시 시도해 주세요"),
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "민감정보 수집 동의가 필요해요"),
    ADULT_CONFIRMATION_REQUIRED(HttpStatus.FORBIDDEN, "만 19세 이상 확인이 필요해요"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청이에요"),
    GROUP_LIMIT(HttpStatus.CONFLICT, "그룹은 3개까지 참여할 수 있어요"),
    GROUP_FULL(HttpStatus.CONFLICT, "그룹 인원이 가득 찼어요"),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 참여한 그룹이에요"),
    ALREADY_QUEUED(HttpStatus.CONFLICT, "이미 그룹을 찾는 중이에요"),
    ALREADY_COMPLETED(HttpStatus.CONFLICT, "오늘 미션을 이미 완료했어요"),
    MISSION_NOT_COMPLETED(HttpStatus.CONFLICT, "미션을 완료하면 핑을 보낼 수 있어요"),
    ALREADY_PINGED(HttpStatus.CONFLICT, "오늘은 이미 핑을 보냈어요"),
    CHECK_NOT_DUE(HttpStatus.CONFLICT, "지금은 마음 점검 기간이 아니에요"),
    NOT_ALLOWED_FOR_GROUP_TYPE(HttpStatus.CONFLICT, "이 그룹에서는 할 수 없어요"),
    INVITE_EXPIRED(HttpStatus.GONE, "만료된 초대 코드예요"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "잠시 뒤 다시 시도해 주세요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 뒤 다시 시도해 주세요");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
