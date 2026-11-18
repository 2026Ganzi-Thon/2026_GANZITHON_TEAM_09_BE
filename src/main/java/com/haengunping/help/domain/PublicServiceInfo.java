package com.haengunping.help.domain;

/**
 * 공공서비스(혜택) API가 알려준 한 사업의 최신 정보 중 우리가 쓰는 칸. 값은 외부 입력이라 검증 전이다.
 * 없는 값은 null이다. 이름·신청 방법·전화는 API 문구가 시드의 손질한 문구와 달라 쓰지 않는다(2026-11-18 결정).
 */
public record PublicServiceInfo(
        String serviceId,
        String deadline,
        String url) {
}
