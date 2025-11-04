package com.unisync.user.credentials.service;

import com.unisync.user.common.config.EncryptionService;
import com.unisync.user.common.entity.CredentialProvider;
import com.unisync.user.common.entity.Credentials;
import com.unisync.user.common.repository.CredentialsRepository;
import com.unisync.user.credentials.dto.CanvasTokenResponse;
import com.unisync.user.credentials.dto.RegisterCanvasTokenRequest;
import com.unisync.user.credentials.dto.RegisterCanvasTokenResponse;
import com.unisync.user.credentials.exception.CanvasTokenNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialsService {

    private final CredentialsRepository credentialsRepository;
    private final EncryptionService encryptionService;
    private final CanvasApiClient canvasApiClient;
    // TODO: SQS Publisher 추가 예정

    /**
     * Canvas 토큰을 등록합니다.
     * 1. Canvas API로 토큰 유효성 검증
     * 2. AES-256으로 암호화하여 저장
     * 3. SQS 이벤트 발행 (user-token-registered)
     *
     * @param userId  사용자 ID (JWT에서 추출)
     * @param request 토큰 등록 요청
     * @return 등록 결과
     */
    @Transactional
    public RegisterCanvasTokenResponse registerCanvasToken(Long userId, RegisterCanvasTokenRequest request) {
        log.info("Registering Canvas token for user: {}", userId);

        // 1. Canvas API로 토큰 유효성 검증
        canvasApiClient.validateToken(request.getCanvasToken());

        // 2. 암호화
        String encryptedToken = encryptionService.encrypt(request.getCanvasToken());

        // 3. DB 저장 (이미 있으면 업데이트)
        Credentials credentials = credentialsRepository
                .findByUserIdAndProvider(userId, CredentialProvider.CANVAS)
                .orElse(Credentials.builder()
                        .userId(userId)
                        .provider(CredentialProvider.CANVAS)
                        .build());

        credentials.setEncryptedToken(encryptedToken);
        credentials.setLastValidatedAt(LocalDateTime.now());

        credentialsRepository.save(credentials);

        log.info("Canvas token registered successfully for user: {}", userId);

        // TODO: 4. SQS 이벤트 발행 (user-token-registered-queue)
        // publishUserTokenRegisteredEvent(userId);

        return RegisterCanvasTokenResponse.builder()
                .success(true)
                .message("Canvas token registered successfully")
                .build();
    }

    /**
     * Canvas 토큰을 조회합니다 (내부 API용).
     * 복호화된 토큰을 반환합니다.
     *
     * @param userId 사용자 ID
     * @return 복호화된 Canvas 토큰
     */
    @Transactional(readOnly = true)
    public CanvasTokenResponse getCanvasToken(Long userId) {
        log.info("Retrieving Canvas token for user: {}", userId);

        Credentials credentials = credentialsRepository
                .findByUserIdAndProvider(userId, CredentialProvider.CANVAS)
                .orElseThrow(() -> new CanvasTokenNotFoundException(
                        "Canvas token not found for user: " + userId));

        // 복호화
        String decryptedToken = encryptionService.decrypt(credentials.getEncryptedToken());

        return CanvasTokenResponse.builder()
                .canvasToken(decryptedToken)
                .lastValidatedAt(credentials.getLastValidatedAt())
                .build();
    }

    /**
     * Canvas 토큰을 삭제합니다.
     *
     * @param userId 사용자 ID
     */
    @Transactional
    public void deleteCanvasToken(Long userId) {
        log.info("Deleting Canvas token for user: {}", userId);

        if (!credentialsRepository.existsByUserIdAndProvider(userId, CredentialProvider.CANVAS)) {
            throw new CanvasTokenNotFoundException("Canvas token not found for user: " + userId);
        }

        credentialsRepository.deleteByUserIdAndProvider(userId, CredentialProvider.CANVAS);

        log.info("Canvas token deleted successfully for user: {}", userId);

        // TODO: Leader 변경 로직 (Course-Service 호출)
    }
}