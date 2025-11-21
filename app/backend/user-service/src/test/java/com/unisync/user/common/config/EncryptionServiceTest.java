package com.unisync.user.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EncryptionService 단위 테스트")
class EncryptionServiceTest {

    private EncryptionService encryptionService;
    private String testEncryptionKey;

    @BeforeEach
    void setUp() throws Exception {
        // 테스트용 AES-256 키 생성
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey key = keyGenerator.generateKey();
        testEncryptionKey = Base64.getEncoder().encodeToString(key.getEncoded());

        encryptionService = new EncryptionService(testEncryptionKey);
    }

    @Test
    @DisplayName("평문 암호화 성공")
    void encrypt_Success() {
        // Given
        String plainText = "test-canvas-token-12345";

        // When
        String encrypted = encryptionService.encrypt(plainText);

        // Then
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEmpty();
        assertThat(encrypted).isNotEqualTo(plainText);
        // Base64 문자열인지 확인
        assertThatNoException().isThrownBy(() -> Base64.getDecoder().decode(encrypted));
    }

    @Test
    @DisplayName("암호문 복호화 성공")
    void decrypt_Success() {
        // Given
        String plainText = "test-canvas-token-12345";
        String encrypted = encryptionService.encrypt(plainText);

        // When
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("암호화-복호화 왕복 - 원본 복원")
    void encryptDecrypt_RoundTrip() {
        // Given
        String originalText = "my-secret-canvas-token";

        // When
        String encrypted = encryptionService.encrypt(originalText);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(originalText);
    }

    @Test
    @DisplayName("같은 평문을 여러 번 암호화하면 다른 암호문 생성 (IV 랜덤)")
    void encrypt_SameTextDifferentResults() {
        // Given
        String plainText = "test-token";

        // When
        String encrypted1 = encryptionService.encrypt(plainText);
        String encrypted2 = encryptionService.encrypt(plainText);

        // Then
        assertThat(encrypted1).isNotEqualTo(encrypted2); // IV가 다르므로 암호문도 다름

        // 하지만 둘 다 복호화하면 원본 평문
        String decrypted1 = encryptionService.decrypt(encrypted1);
        String decrypted2 = encryptionService.decrypt(encrypted2);
        assertThat(decrypted1).isEqualTo(plainText);
        assertThat(decrypted2).isEqualTo(plainText);
    }

    @Test
    @DisplayName("잘못된 암호문 복호화 시 예외 발생")
    void decrypt_InvalidCipherText_ThrowsException() {
        // Given
        String invalidCipherText = "invalid-base64-string-that-cannot-be-decrypted";

        // When & Then
        assertThatThrownBy(() -> encryptionService.decrypt(invalidCipherText))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to decrypt data");
    }

    @Test
    @DisplayName("빈 문자열 암호화-복호화")
    void encryptDecrypt_EmptyString() {
        // Given
        String emptyText = "";

        // When
        String encrypted = encryptionService.encrypt(emptyText);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(emptyText);
    }

    @Test
    @DisplayName("긴 문자열 암호화-복호화")
    void encryptDecrypt_LongString() {
        // Given
        String longText = "a".repeat(10000); // 10KB 문자열

        // When
        String encrypted = encryptionService.encrypt(longText);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(longText);
    }

    @Test
    @DisplayName("특수문자 포함 문자열 암호화-복호화")
    void encryptDecrypt_SpecialCharacters() {
        // Given
        String textWithSpecialChars = "토큰@#$%^&*()_+-=[]{}|;':\",./<>?`~!한글123";

        // When
        String encrypted = encryptionService.encrypt(textWithSpecialChars);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(textWithSpecialChars);
    }

    @Test
    @DisplayName("null 입력 시 예외 발생")
    void encrypt_NullInput_ThrowsException() {
        // When & Then
        assertThatThrownBy(() -> encryptionService.encrypt(null))
                .isInstanceOf(RuntimeException.class);
    }
}
