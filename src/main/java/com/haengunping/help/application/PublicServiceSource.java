package com.haengunping.help.application;

import com.haengunping.help.domain.PublicServiceInfo;
import java.util.Optional;

/** 공공서비스(혜택) 정보의 출처. 실제 구현은 공공데이터포털 API 클라이언트(infra). */
public interface PublicServiceSource {

    /** 호출에 필요한 설정(API 키)이 있는지. 없으면 동기화를 건너뛴다. */
    boolean isConfigured();

    /**
     * 서비스 ID로 최신 정보를 가져온다. 그 ID가 없으면 비어 있다.
     *
     * @throws SyncSourceException 통신 실패·상태 오류·응답 형식 오류. 메시지에는 키·응답 원문이 없다
     */
    Optional<PublicServiceInfo> fetch(String serviceId);
}
