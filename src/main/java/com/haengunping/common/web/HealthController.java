package com.haengunping.common.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배포·프록시가 서버가 떴는지 확인하는 용도. 내부 정보(버전, DB 상태)는 내보내지 않는다. */
@RestController
@RequestMapping(ApiPaths.V1)
public class HealthController {

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
