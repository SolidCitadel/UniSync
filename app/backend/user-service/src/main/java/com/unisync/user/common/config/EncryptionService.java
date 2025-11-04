package com.unisync.user.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKey secretKey;

    public EncryptionService(@Value("${unisync.encryption.key}") String encryptionKey) {
        byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
        this.secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    /**
     * AES-256-GCM을 사용하여 평문을 암호화합니다.
     *
     * @param plainText 암호화할 평문
     * @return Base64로 인코딩된 암호문 (IV + 암호문 포함)
     */
    public String encrypt(String plainText) {
        try {
            // IV 생성 (12바이트)
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);

            // Cipher 초기화
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // 암호화
            byte[] plainTextBytes = plainText.getBytes(StandardCharsets.UTF_8);
            byte[] cipherText = cipher.doFinal(plainTextBytes);

            // IV + 암호문 결합
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            // Base64 인코딩
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * AES-256-GCM을 사용하여 암호문을 복호화합니다.
     *
     * @param encryptedText Base64로 인코딩된 암호문 (IV + 암호문 포함)
     * @return 복호화된 평문
     */
    public String decrypt(String encryptedText) {
        try {
            // Base64 디코딩
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);

            // IV와 암호문 분리
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            // Cipher 초기화
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // 복호화
            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    /**
     * 32바이트(256비트) AES 키를 생성합니다.
     * 환경변수 설정 시 사용하세요.
     *
     * @return Base64로 인코딩된 AES 키
     */
    public static String generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey key = keyGenerator.generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}