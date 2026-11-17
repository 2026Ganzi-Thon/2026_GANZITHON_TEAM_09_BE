package com.haengunping.notification.infra.webpush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** RFC 8291 부록 A(https://www.rfc-editor.org/rfc/rfc8291#appendix-A)의 공식 값과 바이트 단위로 대조한다. */
class WebPushCryptoTest {

    static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    // 부록 A의 86바이트 헤더와 암호문(공백 제거). base64는 따로 풀어 이어 붙인다(86은 3의 배수가 아니라 문자열로는 못 잇는다).
    private static final String EXPECTED_HEADER =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String EXPECTED_CIPHERTEXT =
            "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ";

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    @Test
    void NOTI01_RFC_8291_부록_A의_결과와_같다() throws Exception {
        KeyPair asKeys = new KeyPair(WebPushCrypto.publicKey(B64.decode(AS_PUBLIC)), WebPushCrypto.privateKey(B64.decode(AS_PRIVATE)));

        byte[] result = WebPushCrypto.encrypt(
                PLAINTEXT.getBytes(StandardCharsets.UTF_8), B64.decode(UA_PUBLIC), B64.decode(AUTH_SECRET), asKeys, B64.decode(SALT));

        assertThat(result).isEqualTo(expected());
    }

    @Test
    void 공개키는_65바이트_비압축_형식으로_왕복한다() throws Exception {
        byte[] raw = B64.decode(UA_PUBLIC);
        assertThat(WebPushCrypto.encode(WebPushCrypto.publicKey(raw))).isEqualTo(raw);
        // 좌표 앞자리가 0이어도 32바이트를 유지하는지 무작위 키로 반복 확인
        for (int i = 0; i < 50; i++) {
            assertThat(WebPushCrypto.encode((java.security.interfaces.ECPublicKey) WebPushCrypto.newKeyPair().getPublic())).hasSize(65);
        }
    }

    @Test
    void 형식이_틀린_키와_너무_긴_평문은_거부한다() throws Exception {
        assertThatThrownBy(() -> WebPushCrypto.publicKey(new byte[64])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebPushCrypto.privateKey(new byte[31])).isInstanceOf(IllegalArgumentException.class);
        KeyPair keys = WebPushCrypto.newKeyPair();
        assertThatThrownBy(() -> WebPushCrypto.encrypt(
                new byte[WebPushCrypto.MAX_PLAINTEXT + 1], B64.decode(UA_PUBLIC), B64.decode(AUTH_SECRET), keys, new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] expected() {
        byte[] header = B64.decode(EXPECTED_HEADER);
        byte[] ciphertext = B64.decode(EXPECTED_CIPHERTEXT);
        byte[] all = java.util.Arrays.copyOf(header, header.length + ciphertext.length);
        System.arraycopy(ciphertext, 0, all, header.length, ciphertext.length);
        assertThat(header).hasSize(86);
        return all;
    }
}
