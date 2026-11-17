package com.haengunping.help.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.haengunping.help.application.SyncSourceException;
import com.haengunping.help.domain.PublicServiceInfo;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 외부 API는 가짜 서버로만 확인한다. 키는 헤더로만 나가고, 실패 사유에 키·응답 원문이 없어야 한다(SEC-11). */
class GovServiceClientTest {

    private static final String KEY = "SECRET-KEY-VALUE";
    private static final String BASE = "https://fake.example.kr/api/gov24/v3";

    private static final String OK_BODY = """
            {"currentCount":1,"data":[{"서비스ID":"135200005045","서비스명":"정신건강 심리상담 바우처",
              "신청방법":"온라인 신청","신청기한":"상시신청","전화문의":"129","상세조회URL":"https://www.gov.kr/portal/x"}],
             "matchCount":1,"page":1,"perPage":1,"totalCount":1}""";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private GovServiceClient client(String key, int attempts) {
        return new GovServiceClient(builder, JsonMapper.builder().build(), key, BASE, attempts, Duration.ZERO);
    }

    @Test
    void 키는_serviceKey_파라미터로_보낸다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/serviceList")))
                .andExpect(requestTo(org.hamcrest.Matchers.containsString("serviceKey=" + KEY)))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        Optional<PublicServiceInfo> info = client(KEY, 1).fetch("135200005045");

        assertThat(info).contains(new PublicServiceInfo("135200005045", "상시신청", "https://www.gov.kr/portal/x"));
        server.verify();
    }

    @Test
    void 키가_없으면_설정_안_됨으로_본다() {
        assertThat(client("", 1).isConfigured()).isFalse();
        assertThat(client("  ", 1).isConfigured()).isFalse();
        assertThat(client(KEY, 1).isConfigured()).isTrue();
    }

    @Test
    void 결과가_비면_빈_값이다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/serviceList")))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client(KEY, 1).fetch("135200005045")).isEmpty();
    }

    @Test
    void 다른_서비스ID_행은_무시한다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/serviceList")))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertThat(client(KEY, 1).fetch("OTHER")).isEmpty();
    }

    @Test
    void 서버_오류는_상한까지_재시도한다() {
        server.expect(ExpectedCount.times(3), requestTo(org.hamcrest.Matchers.containsString("/serviceList")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client(KEY, 3).fetch("135200005045"))
                .isInstanceOf(SyncSourceException.class)
                .hasMessage("HTTP 500");
        server.verify();
    }

    @Test
    void 일시_오류_뒤_성공하면_결과를_준다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/serviceList"))).andRespond(withServerError());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/serviceList")))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertThat(client(KEY, 3).fetch("135200005045")).isPresent();
    }

    @Test
    void 클라이언트_오류는_다시_시도하지_않는다() {
        server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.containsString("/serviceList")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"msg\":\"" + KEY + "\"}"));

        assertThatThrownBy(() -> client(KEY, 3).fetch("135200005045"))
                .isInstanceOf(SyncSourceException.class)
                .hasMessage("HTTP 401")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(KEY));
        server.verify();
    }

    @Test
    void 형식이_다른_응답은_오류다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/serviceList")))
                .andRespond(withSuccess("<html>not json " + KEY + "</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client(KEY, 1).fetch("135200005045"))
                .isInstanceOf(SyncSourceException.class)
                .hasMessage("응답 형식 오류");
    }

    @Test
    void data가_배열이_아니면_오류다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/serviceList")))
                .andRespond(withSuccess("{\"data\":\"x\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(KEY, 1).fetch("135200005045"))
                .isInstanceOf(SyncSourceException.class)
                .hasMessage("응답 형식 오류");
    }
}
