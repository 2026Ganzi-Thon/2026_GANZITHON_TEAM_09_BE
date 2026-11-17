package com.haengunping.notification.infra.webpush;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 웹 푸시 메시지 암호화(RFC 8291, aes128gcm). JDK 표준 암호 기능만 쓴다.
 * 외부 라이브러리 후보(nl.martijndwars:web-push)가 유지보수 기준에 걸려 직접 구현했다(ai-usage.md 4절).
 * RFC 8291 부록 A의 공식 테스트 벡터와 바이트 단위로 대조한다(WebPushCryptoTest).
 */
final class WebPushCrypto {

    static final int RECORD_SIZE = 4096;
    private static final int KEY_LENGTH = 65; // 0x04 || X(32) || Y(32)
    private static final int TAG_LENGTH = 16;
    private static final int HEADER_LENGTH = 16 + 4 + 1 + KEY_LENGTH;
    /** 레코드 하나에 들어가는 최대 평문: 4096 - 헤더 86 - 태그 16 - 구분자 1. */
    static final int MAX_PLAINTEXT = RECORD_SIZE - HEADER_LENGTH - TAG_LENGTH - 1;

    private static final ECParameterSpec P256 = p256();

    private WebPushCrypto() {
    }

    /**
     * 평문을 받는 기기만 풀 수 있게 암호화한다. 결과 = 헤더(salt, rs, 서버 임시 공개키) || 암호문.
     *
     * @param uaPublic   구독의 p256dh(기기 공개키, 65바이트)
     * @param authSecret 구독의 auth(16바이트)
     * @param asKeys     메시지마다 새로 만드는 서버 임시 키 쌍
     * @param salt       메시지마다 새로 만드는 16바이트 무작위 값
     */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair asKeys, byte[] salt)
            throws GeneralSecurityException {
        if (plaintext.length > MAX_PLAINTEXT || salt.length != 16) {
            throw new IllegalArgumentException("평문은 " + MAX_PLAINTEXT + "바이트 이하, salt는 16바이트");
        }
        byte[] asPublic = encode((ECPublicKey) asKeys.getPublic());

        KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
        ecdh.init(asKeys.getPrivate());
        ecdh.doPhase(publicKey(uaPublic), true);
        byte[] ecdhSecret = ecdh.generateSecret();

        // RFC 8291 3.4
        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hmac(prkKey, concat(keyInfo, new byte[] {1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(TAG_LENGTH * 8, nonce));
        byte[] ciphertext = aes.doFinal(concat(plaintext, new byte[] {2})); // 0x02: 마지막 레코드 구분자

        ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH)
                .put(salt).putInt(RECORD_SIZE).put((byte) KEY_LENGTH).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /** 65바이트 비압축 점 → 공개키. 곡선 위의 점이 아니면 KeyAgreement가 거부한다. */
    static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != KEY_LENGTH || uncompressed[0] != 4) {
            throw new IllegalArgumentException("P-256 비압축 공개키(65바이트)가 아니다");
        }
        ECPoint point = new ECPoint(
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    /** 32바이트 원시 값 → 개인키(VAPID 개인키 형식). */
    static ECPrivateKey privateKey(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 32) {
            throw new IllegalArgumentException("P-256 개인키(32바이트)가 아니다");
        }
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), P256));
    }

    static byte[] encode(ECPublicKey key) {
        return concat(new byte[] {4}, unsigned32(key.getW().getAffineX()), unsigned32(key.getW().getAffineY()));
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] bytes = value.toByteArray(); // 부호 바이트가 붙거나 앞의 0이 빠질 수 있다
        byte[] out = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, out, 32 - copy, copy);
        return out;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }

    private static ECParameterSpec p256() {
        try {
            return ((ECPublicKey) newKeyPair().getPublic()).getParams();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JDK에 P-256이 없다", e);
        }
    }
}
