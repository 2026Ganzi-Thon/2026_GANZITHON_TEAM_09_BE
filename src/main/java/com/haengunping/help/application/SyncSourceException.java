package com.haengunping.help.application;

/** 공공서비스 API에서 정보를 가져오지 못했다. 메시지는 {@code service_sync_log.error}에 남으므로 키·응답 원문을 담지 않는다. */
public class SyncSourceException extends RuntimeException {

    public SyncSourceException(String message) {
        super(message);
    }

    public SyncSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
