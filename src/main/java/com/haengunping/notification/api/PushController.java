package com.haengunping.notification.api;

import com.haengunping.common.web.ApiPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 없이 여는 웹 푸시 공개키(api.md 9절). 기기가 구독을 만들 때 쓴다. 공개값이라 숨길 필요가 없다. */
@RestController
public class PushController {

    private final VapidPublicKeyResponse response;

    public PushController(@Value("${haengunping.vapid.public-key:}") String publicKey) {
        this.response = new VapidPublicKeyResponse(publicKey);
    }

    @GetMapping(ApiPaths.V1 + "/push/vapid-public-key")
    public VapidPublicKeyResponse vapidPublicKey() {
        return response;
    }

    public record VapidPublicKeyResponse(String publicKey) {
    }
}
