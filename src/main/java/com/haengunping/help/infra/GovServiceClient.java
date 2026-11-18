package com.haengunping.help.infra;

import com.haengunping.help.application.PublicServiceSource;
import com.haengunping.help.application.SyncSourceException;
import com.haengunping.help.domain.PublicServiceInfo;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 공공데이터포털 「행정안전부 대한민국 공공서비스(혜택) 정보」 서비스목록(serviceList) 클라이언트(HELP-04).
 *
 * <ul>
 *   <li>키는 {@code serviceKey} 쿼리 파라미터로 보낸다(실제 호출로 확인: {@code Authorization: Infuser} 헤더는 400).
 *       URL에 키가 들어가므로 요청 URL을 로그·예외 메시지에 절대 남기지 않는다(SEC-11).
 *       이 패키지의 로그 레벨을 DEBUG로 올리지 않는다.</li>
 *   <li>연결·읽기 시간 제한과 재시도 상한(기본 3번, 서버 오류·통신 실패만)을 둔다.</li>
 *   <li>예외 메시지에는 상태 코드·종류만 담는다. 키와 응답 원문은 담지 않는다.</li>
 * </ul>
 *
 * <p>응답 필드명(서비스ID·서비스명·신청방법·신청기한·전화문의·상세조회URL)은 공공데이터포털 명세를 따른다.
 * 실제 키로 처음 호출할 때 명세와 맞는지 확인할 것(docs/deploy.md).
 */
@Component
public class GovServiceClient implements PublicServiceSource {

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String serviceKey;
    private final int maxAttempts;
    private final Duration retryDelay;

    @Autowired
    GovServiceClient(
            ObjectMapper mapper,
            @Value("${DATA_GO_KR_KEY:}") String serviceKey,
            @Value("${haengunping.help.sync.base-url:https://api.odcloud.kr/api/gov24/v3}") String baseUrl,
            @Value("${haengunping.help.sync.timeout:PT10S}") Duration timeout,
            @Value("${haengunping.help.sync.max-attempts:3}") int maxAttempts,
            @Value("${haengunping.help.sync.retry-delay:PT1S}") Duration retryDelay) {
        this(timeoutBuilder(timeout), mapper, serviceKey, baseUrl, maxAttempts, retryDelay);
    }

    /** 테스트에서 가짜 서버를 붙인 빌더를 넣는 생성자. */
    GovServiceClient(RestClient.Builder builder, ObjectMapper mapper, String serviceKey, String baseUrl,
            int maxAttempts, Duration retryDelay) {
        this.client = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
        this.serviceKey = serviceKey == null ? "" : serviceKey.strip();
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 5));
        this.retryDelay = retryDelay;
    }

    private static RestClient.Builder timeoutBuilder(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory);
    }

    @Override
    public boolean isConfigured() {
        return !serviceKey.isEmpty();
    }

    @Override
    public Optional<PublicServiceInfo> fetch(String serviceId) {
        if (!isConfigured()) {
            throw new SyncSourceException("API 키 없음");
        }
        String body = getWithRetry(serviceId);
        return parse(serviceId, body);
    }

    private String getWithRetry(String serviceId) {
        SyncSourceException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return client.get()
                        .uri(uriBuilder -> uriBuilder.path("/serviceList")
                                .queryParam("page", 1)
                                .queryParam("perPage", 1)
                                .queryParam("returnType", "JSON")
                                .queryParam("cond[서비스ID::EQ]", "{id}")
                                .queryParam("serviceKey", "{key}")
                                .build(serviceId, serviceKey))
                        .retrieve()
                        .body(String.class);
            } catch (HttpStatusCodeException e) {
                last = new SyncSourceException("HTTP " + e.getStatusCode().value());
                if (e.getStatusCode().is4xxClientError()) {
                    throw last; // 키·요청 오류는 다시 해도 같다
                }
            } catch (RestClientException e) {
                last = new SyncSourceException("통신 실패");
            }
            pause();
        }
        throw last;
    }

    private void pause() {
        if (retryDelay.isZero()) {
            return;
        }
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SyncSourceException("중단됨");
        }
    }

    private Optional<PublicServiceInfo> parse(String serviceId, String body) {
        JsonNode data;
        try {
            data = mapper.readTree(body).path("data");
        } catch (RuntimeException e) {
            throw new SyncSourceException("응답 형식 오류");
        }
        if (!data.isArray()) {
            throw new SyncSourceException("응답 형식 오류");
        }
        for (JsonNode row : data) {
            if (serviceId.equals(text(row, "서비스ID"))) {
                return Optional.of(new PublicServiceInfo(
                        serviceId,
                        text(row, "신청기한"),
                        text(row, "상세조회URL")));
            }
        }
        return Optional.empty();
    }

    private static String text(JsonNode row, String field) {
        JsonNode node = row.path(field);
        return node.isTextual() ? node.asString() : null;
    }
}
