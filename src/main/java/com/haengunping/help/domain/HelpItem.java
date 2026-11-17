package com.haengunping.help.domain;

/**
 * 도움받을 곳 한 항목(help_items 한 행). 사용자 정보와 관계없는 공개 데이터다.
 *
 * @param rulesJson 표시 조건 JSON(minAge·maxAge·sido·sigungu·jobs). 서버가 정한 시드·동기화 값만 들어간다
 */
public record HelpItem(
        String id,
        String section,
        String kind,
        String name,
        String summary,
        String target,
        String note,
        String phone,
        String url,
        String address,
        String applyMethod,
        String deadline,
        String rulesJson,
        String sourceServiceId) {
}
