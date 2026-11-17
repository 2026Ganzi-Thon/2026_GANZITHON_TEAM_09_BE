package com.haengunping.common.error;
import org.springframework.http.HttpStatus;
public class ApiFault extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Integer retryAfter;
    public ApiFault(HttpStatus status, String code, String message) { this(status, code, message, null); }
    public ApiFault(HttpStatus status, String code, String message, Integer retryAfter) {
        super(message); this.status=status; this.code=code; this.retryAfter=retryAfter;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Integer retryAfter() { return retryAfter; }
    public static ApiFault notFound() { return new ApiFault(HttpStatus.NOT_FOUND,"NOT_FOUND","찾을 수 없어요"); }
    public static ApiFault validation(String message) { return new ApiFault(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED",message); }
    public static ApiFault conflict(String code,String message) { return new ApiFault(HttpStatus.CONFLICT,code,message); }
}
