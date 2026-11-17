package com.haengunping.notification.infra.webpush;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 푸시 서비스에 우리 서버임을 증명하는 VAPID 헤더(RFC 8292). ES256 서명 JWT.
 * 키 형식은 web-push 도구들과 같다: 공개키 65바이트, 개인키 32바이트, 둘 다 base64url.
 */
final class Vapid {

    /** RFC 8292: exp는 24시간 이내. 여유를 두고 12시간. */
    static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final String JWT_HEADER = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));

    private final ECPrivateKey privateKey;
    private final String publicKey;
    private final String subject;

    Vapid(String publicKey, String privateKey, String subject) throws GeneralSecurityException {
        if (subject.contains("\"") || subject.contains("\\") || !(subject.startsWith("mailto:") || subject.startsWith("https:"))) {
            throw new IllegalArgumentException("VAPID_SUBJECT는 mailto: 또는 https: 주소");
        }
        WebPushCrypto.publicKey(Base64.getUrlDecoder().decode(publicKey)); // 형식 검사
        this.privateKey = WebPushCrypto.privateKey(Base64.getUrlDecoder().decode(privateKey));
        this.publicKey = publicKey;
        this.subject = subject;
    }

    /** Authorization 헤더 값: {@code vapid t=<JWT>, k=<공개키>}. aud는 endpoint의 origin. */
    String authorization(URI endpoint, Instant now) throws GeneralSecurityException {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        String claims = "{\"aud\":\"" + audience + "\",\"exp\":" + now.plus(TOKEN_LIFETIME).getEpochSecond()
                + ",\"sub\":\"" + subject + "\"}";
        String signingInput = JWT_HEADER + "." + B64.encodeToString(claims.getBytes(StandardCharsets.UTF_8));

        Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format"); // JWT가 요구하는 r||s 64바이트 형식
        es256.initSign(privateKey);
        es256.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        String jwt = signingInput + "." + B64.encodeToString(es256.sign());
        return "vapid t=" + jwt + ", k=" + publicKey;
    }
}
