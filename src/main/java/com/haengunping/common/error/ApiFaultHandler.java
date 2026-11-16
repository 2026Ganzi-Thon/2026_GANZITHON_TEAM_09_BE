
package com.haengunping.common.error;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A 담당 모듈에서 발생하는 ApiFault 예외만 처리한다.
 *
 * JSON 파싱 오류, 입력값 검증 오류 등은
 * 기존 GlobalExceptionHandler에서 처리한다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiFaultHandler {

    @ExceptionHandler(ApiFault.class)
    public ResponseEntity<Map<String, Object>> fault(ApiFault e) {

        HttpHeaders headers = new HttpHeaders();

        if (e.retryAfter() != null) {
            headers.set(
                    "Retry-After",
                    String.valueOf(e.retryAfter())
            );
        }

        return new ResponseEntity<>(
                Map.of(
                        "code", e.code(),
                        "message", e.getMessage()
                ),
                headers,
                e.status()
        );
    }
}
