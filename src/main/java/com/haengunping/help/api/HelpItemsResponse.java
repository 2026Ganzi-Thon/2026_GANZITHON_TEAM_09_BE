package com.haengunping.help.api;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.haengunping.help.application.HelpItemsService.HelpItems;
import com.haengunping.help.domain.HelpItem;
import java.time.Instant;
import java.util.List;

/** {@code GET /help/items} 응답(docs/api.md 8절). 프론트 {@code HelpItemsResponse}와 같은 모양. */
record HelpItemsResponse(String version, Instant syncedAt, List<Item> items) {

    static HelpItemsResponse from(HelpItems source) {
        return new HelpItemsResponse(
                source.version(),
                source.syncedAt(),
                source.items().stream().map(Item::from).toList());
    }

    /**
     * 항목 하나. 값이 없는 필드도 null로 보낸다(프론트 타입이 {@code string | null}).
     * {@code rules}는 DB의 jsonb를 그대로 싣는다. 서버가 정한 시드·동기화 값만 들어가며 사용자 입력은 없다.
     */
    record Item(
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
            @JsonRawValue String rules,
            String sourceServiceId) {

        static Item from(HelpItem x) {
            return new Item(x.id(), x.section(), x.kind(), x.name(), x.summary(), x.target(), x.note(),
                    x.phone(), x.url(), x.address(), x.applyMethod(), x.deadline(), x.rulesJson(), x.sourceServiceId());
        }
    }
}
