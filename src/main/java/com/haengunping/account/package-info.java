/**
 * 익명 계정, 세션 쿠키 hp_session, 동의 (담당 A).
 *
 * <p>모듈 루트 패키지에는 다른 모듈에 공개하는 것만 둔다: {@code XxxQuery} 인터페이스와 이벤트 레코드.
 * 내부는 {@code api}(컨트롤러·DTO) / {@code application}(서비스) / {@code domain}(엔티티·규칙) /
 * {@code infra}(리포지토리·외부 API)로 나누고, 다른 모듈은 내부를 쓰지 않는다(ArchitectureTest).
 */
package com.haengunping.account;
