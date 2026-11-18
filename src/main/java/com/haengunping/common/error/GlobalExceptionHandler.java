package com.haengunping.common.error;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpHeaders;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * 모든 예외를 {@code {code, message}}로 바꾸는 곳(SEC-12).
 * 시큐리티 단계의 거절(401·403)도 {@code SecurityConfig}가 이곳으로 넘긴다.
 * 응답에 스택·SQL·클래스 이름을 넣지 않는다. 예상 밖 예외만 서버 로그에 남긴다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.code(), ErrorResponse.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .toList();
        return validationFailed(fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleInvalidParameter(HandlerMethodValidationException e) {
        List<String> fields = e.getParameterValidationResults().stream()
                .map(r -> r.getMethodParameter().getParameterName())
                .distinct()
                .sorted()
                .toList();
        return validationFailed(fields);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return validationFailed(List.of(e.getParameterName()));
    }

    /*
     * 본문을 읽지 못한 요청. api.md 공통 규칙에 따라 둘로 가른다(2026-11-18 확정).
     * - 정의 밖 열거값: "값이 틀렸다" → VALIDATION_FAILED + 필드 이름(화면이 어느 칸인지 알 수 있게)
     * - 깨진 JSON, 숫자 자리에 문자열 같은 타입 불일치: MALFORMED_REQUEST
     * 열거값 자리의 숫자도 열거값 오류다(application.yml에서 순서로 받는 것을 꺼 둠).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        MismatchedInputException mismatch = causeOf(e, MismatchedInputException.class);
        if (mismatch != null && isEnum(mismatch.getTargetType())) {
            return validationFailed(fieldPath(mismatch));
        }
        return respond(ErrorCode.MALFORMED_REQUEST);
    }

    // 쿼리·경로 파라미터의 타입 불일치. 위와 같은 기준으로 열거값만 VALIDATION_FAILED.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        if (isEnum(e.getRequiredType())) {
            return validationFailed(List.of(e.getName()));
        }
        return respond(ErrorCode.MALFORMED_REQUEST);
    }

    private static boolean isEnum(Class<?> type) {
        return type != null && type.isEnum();
    }

    /** Jackson 경로를 필드 이름으로: items[1].color. 최상위가 열거값이라 이름이 없으면 빈 목록. */
    private static List<String> fieldPath(JacksonException e) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference ref : e.getPath()) {
            if (ref.getPropertyName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? List.of() : List.of(path.toString());
    }

    private static <T extends Throwable> T causeOf(Throwable e, Class<T> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoPath(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> handleUnauthenticated(AuthenticationException e) {
        return respond(ErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(CsrfException.class)
    ResponseEntity<ErrorResponse> handleCsrf(CsrfException e) {
        return respond(ErrorCode.CSRF_INVALID);
    }

    // CSRF 외의 권한 거절은 존재 여부를 숨기려고 404로 답한다(SEC-02).
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleDenied(AccessDeniedException e) {
        return respond(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("예상 밖 오류", e);
        return respond(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> validationFailed(List<String> fields) {
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return respond(code, new ErrorResponse(code.name(), code.defaultMessage(), fields));
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code) {
        return respond(code, ErrorResponse.of(code));
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, ErrorResponse body) {
        return ResponseEntity.status(code.status()).body(body);
    }
    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ErrorResponse> handleRateLimited(
            RateLimitedException e) {

        return ResponseEntity
                .status(ErrorCode.RATE_LIMITED.status())
                .header(
                        HttpHeaders.RETRY_AFTER,
                        String.valueOf(e.retryAfterSeconds())
                )
                .body(ErrorResponse.of(
                        ErrorCode.RATE_LIMITED,
                        e.getMessage()
                ));
    }
}
