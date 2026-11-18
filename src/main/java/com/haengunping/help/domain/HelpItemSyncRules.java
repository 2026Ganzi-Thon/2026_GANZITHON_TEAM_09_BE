package com.haengunping.help.domain;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 외부 API 값을 도움받을 곳 항목에 반영하는 규칙(HELP-04).
 * 바뀔 수 있는 사실 정보인 신청 기한과 상세 URL만 덮어쓴다. 이름·신청 방법·전화는 손질한 시드 문구를 유지한다.
 * 외부 값은 믿지 않는다: 비었거나 형식·길이가 어긋나면 그 칸만 기존 값을 유지한다.
 * 길이 상한은 help_items 컬럼 크기와 같다.
 */
public final class HelpItemSyncRules {

    static final int DEADLINE_MAX = 60;
    static final int URL_MAX = 500;

    private HelpItemSyncRules() {
    }

    /** 기존 항목에 외부 정보를 덮어쓴 새 항목. 바뀐 게 없으면 equal한 값이 나온다. */
    public static HelpItem merge(HelpItem current, PublicServiceInfo info) {
        return new HelpItem(
                current.id(), current.section(), current.kind(), current.name(),
                current.summary(), current.target(), current.note(), current.phone(),
                pickUrl(info.url(), current.url()),
                current.address(), current.applyMethod(),
                pick(info.deadline(), DEADLINE_MAX, current.deadline()),
                current.rulesJson(), current.sourceServiceId());
    }

    private static String pick(String incoming, int max, String fallback) {
        String value = incoming == null ? "" : incoming.strip();
        return value.isEmpty() || value.length() > max ? fallback : value;
    }

    /** https만 받는다. 그 밖의 스킴(http·javascript: 등)과 사용자 정보가 든 주소는 버린다. */
    private static String pickUrl(String incoming, String fallback) {
        String value = pick(incoming, URL_MAX, null);
        if (value == null) {
            return fallback;
        }
        try {
            URI uri = new URI(value);
            boolean ok = "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
            return ok ? value : fallback;
        } catch (URISyntaxException e) {
            return fallback;
        }
    }
}
