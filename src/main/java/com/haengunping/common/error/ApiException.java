package com.haengunping.common.error;

/**
 * 서비스 규칙 위반을 알리는 예외. 던지면 {@link GlobalExceptionHandler}가 {@code {code, message}}로 바꾼다.
 * 메시지는 화면에 그대로 보일 수 있으므로 내부 정보(ID, SQL, 클래스 이름)를 넣지 않는다.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
