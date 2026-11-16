package com.haengunping.common.web;

/**
 * API 기본 경로. 컨트롤러는 {@code @RequestMapping(ApiPaths.V1 + "/groups")}처럼 쓴다.
 * {@code server.servlet.context-path}를 쓰지 않는 이유: 쿠키 경로가 {@code /api/v1}로 묶여
 * 프론트(경로 {@code /})가 {@code XSRF-TOKEN} 쿠키를 읽지 못한다.
 */
public final class ApiPaths {

    public static final String V1 = "/api/v1";

    private ApiPaths() {
    }
}
