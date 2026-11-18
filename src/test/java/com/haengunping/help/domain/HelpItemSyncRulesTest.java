package com.haengunping.help.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** HELP-04: 기한·URL만 덮어쓰고, 외부 값은 검증하며, 어긋나면 그 칸만 기존 값을 유지한다. */
class HelpItemSyncRulesTest {

    private static final HelpItem CURRENT = new HelpItem(
            "voucher", "COUNSELING", "SERVICE", "기존 이름", "요약", "대상", "비고",
            "1234", "https://old.example.kr/a", "주소", "기존 신청 방법", "기존 기한",
            "{\"minAge\": 19}", "135200005045");

    private static PublicServiceInfo info(String deadline, String url) {
        return new PublicServiceInfo("135200005045", deadline, url);
    }

    @Test
    void 유효한_기한과_URL을_덮어쓴다() {
        HelpItem merged = HelpItemSyncRules.merge(CURRENT, info("상시신청", "https://plus.gov.kr/x"));

        assertThat(merged.deadline()).isEqualTo("상시신청");
        assertThat(merged.url()).isEqualTo("https://plus.gov.kr/x");
    }

    @Test
    void 이름_신청방법_전화와_손으로_정한_칸은_건드리지_않는다() {
        HelpItem merged = HelpItemSyncRules.merge(CURRENT, info("상시신청", "https://a.kr"));

        assertThat(merged.id()).isEqualTo("voucher");
        assertThat(merged.name()).isEqualTo("기존 이름");
        assertThat(merged.applyMethod()).isEqualTo("기존 신청 방법");
        assertThat(merged.phone()).isEqualTo("1234");
        assertThat(merged.summary()).isEqualTo("요약");
        assertThat(merged.target()).isEqualTo("대상");
        assertThat(merged.note()).isEqualTo("비고");
        assertThat(merged.address()).isEqualTo("주소");
        assertThat(merged.rulesJson()).isEqualTo("{\"minAge\": 19}");
        assertThat(merged.sourceServiceId()).isEqualTo("135200005045");
    }

    @Test
    void 비었거나_없는_값은_기존_값을_유지한다() {
        assertThat(HelpItemSyncRules.merge(CURRENT, info(null, null))).isEqualTo(CURRENT);
        assertThat(HelpItemSyncRules.merge(CURRENT, info("   ", ""))).isEqualTo(CURRENT);
    }

    @Test
    void 길이가_컬럼보다_길면_그_칸만_유지한다() {
        HelpItem merged = HelpItemSyncRules.merge(CURRENT, info("다".repeat(61), "https://a.kr/" + "x".repeat(500)));

        assertThat(merged).isEqualTo(CURRENT);
    }

    @Test
    void 길이가_딱_상한이면_받는다() {
        HelpItem merged = HelpItemSyncRules.merge(CURRENT, info("다".repeat(60), null));

        assertThat(merged.deadline()).hasSize(60);
    }

    @Test
    void 앞뒤_공백은_자르고_받는다() {
        assertThat(HelpItemSyncRules.merge(CURRENT, info("  상시신청  ", null)).deadline()).isEqualTo("상시신청");
    }

    @Test
    void URL은_https만_받는다() {
        String old = CURRENT.url();
        for (String bad : new String[] {
                "http://www.gov.kr/x", "javascript:alert(1)", "//evil.example/x", "ftp://a.kr/x",
                "https://user:pw@gov.kr/x", "https:///nohost", "https://exa mple.kr", "www.gov.kr/x"}) {
            assertThat(HelpItemSyncRules.merge(CURRENT, info(null, bad)).url()).as(bad).isEqualTo(old);
        }
        assertThat(HelpItemSyncRules.merge(CURRENT, info(null, "HTTPS://www.gov.kr/x")).url())
                .isEqualTo("HTTPS://www.gov.kr/x");
    }
}
