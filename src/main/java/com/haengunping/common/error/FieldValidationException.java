package com.haengunping.common.error;

import java.util.List;

/**
 * 컨트롤러가 직접 검증하다 어긋난 칸을 알리는 예외. {@code VALIDATION_FAILED}에 {@code fields}를 싣는다.
 * 보통은 {@code @Valid}로 충분하지만, 검증 앞에 다른 검사(예: 동의 여부 403)가 와야 하면 이 예외를 쓴다.
 * 칸 이름은 {@code answers}, {@code answers[2]}처럼 요청 본문의 경로다.
 */
public class FieldValidationException extends ApiException {

    private final List<String> fields;

    public FieldValidationException(List<String> fields) {
        super(ErrorCode.VALIDATION_FAILED);
        this.fields = fields.stream().distinct().sorted().toList();
    }

    public List<String> fields() {
        return fields;
    }
}
