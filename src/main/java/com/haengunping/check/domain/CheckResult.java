package com.haengunping.check.domain;

/**
 * 주간 점검 결과 코드와 결과 문장(CHK-03·06). 문장은 기획서 「주 1회 마음 점검」 결과 기준 표와 프론트
 * {@code checkinRules.ts}의 문장과 글자까지 같게 둔다. 진단이 아니라 참고용이다.
 */
public enum CheckResult {
    DEPRESSION("요즘 많이 지쳐 있었던 것 같아요"),
    ANXIETY("걱정이 많았던 한 주였던 것 같아요"),
    LOW_MOOD_DAYS("기분이 가라앉은 날이 많았네요"),
    STABLE("이번 주는 크게 흔들리지 않았던 것 같아요");

    private final String message;

    CheckResult(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
