package com.haengunping.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 모든 에러 응답의 모양(SEC-12). {@code fields}는 VALIDATION_FAILED일 때만 들어간다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<String> fields) {

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(), message, null);
    }

    public static ErrorResponse of(ErrorCode code) {
        return of(code, code.defaultMessage());
    }
}
