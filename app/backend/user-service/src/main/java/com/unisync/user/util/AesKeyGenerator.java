package com.unisync.user.util;

import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * AES-256 암호화 키 생성 유틸리티
 *
 * 실행 방법:
 * ./gradlew run --args="generate-key"
 */
public class AesKeyGenerator {

    public static void main(String[] args) {
        try {
            String key = generateAesKey();
            System.out.println("Generated AES-256 Key (Base64):");
            System.out.println(key);
            System.out.println("\nAdd this to your .env file:");
            System.out.println("ENCRYPTION_KEY=" + key);
        } catch (Exception e) {
            System.err.println("Failed to generate key: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String generateAesKey() throws NoSuchAlgorithmException {
        javax.crypto.KeyGenerator keyGenerator = javax.crypto.KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey key = keyGenerator.generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}