package com.unisync.user.credentials.service;

import com.unisync.shared.dto.sqs.UserTokenRegisteredEvent;
import com.unisync.user.common.config.EncryptionService;
import com.unisync.user.common.entity.CredentialProvider;
import com.unisync.user.common.entity.Credentials;
import com.unisync.user.common.entity.User;
import com.unisync.user.common.repository.CredentialsRepository;
import com.unisync.user.common.repository.UserRepository;
import com.unisync.user.common.service.SqsPublisher;
import com.unisync.user.auth.exception.UserNotFoundException;
import com.unisync.user.credentials.dto.CanvasTokenResponse;
import com.unisync.user.credentials.dto.RegisterCanvasTokenRequest;
import com.unisync.user.credentials.dto.RegisterCanvasTokenResponse;
import com.unisync.user.credentials.exception.CanvasTokenNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final SqsPublisher sqsPublisher;
    private final UserRepository userRepository;

    @Value("${aws.sqs.queues.user-token-registered}")
    private String userTokenRegisteredQueue;

    /**
     * Canvas 토큰을 등록합니다.
     * 1. Canvas API로 토큰 유효성 검증 + 프로필 조회
     * 2. AES-256으로 암호화하여 저장
     * 3. 연동 정보 저장 (is_connected, external_user_id, external_username)
     * 4. SQS 이벤트 발행 (user-token-registered)
     *
     * @param cognitoSub Cognito 사용자 ID (JWT에서 추출)
     * @param request    토큰 등록 요청
     * @return 등록 결과
     */
    @Transactional
    public RegisterCanvasTokenResponse registerCanvasToken(String cognitoSub, RegisterCanvasTokenRequest request) {
        log.info("Registering Canvas token for cognitoSub: {}", cognitoSub);

        // 1. Canvas API로 토큰 유효성 검증 + 프로필 조회
        CanvasApiClient.CanvasProfile profile = canvasApiClient.validateTokenAndGetProfile(
                request.getCanvasToken()
        );

        // 2. 암호화
        String encryptedToken = encryptionService.encrypt(request.getCanvasToken());

        // 3. DB 저장 (이미 있으면 업데이트)
        Credentials credentials = credentialsRepository
                .findByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS)
                .orElse(Credentials.builder()
                        .cognitoSub(cognitoSub)
                        .provider(CredentialProvider.CANVAS)
                        .build());

        credentials.setEncryptedToken(encryptedToken);
        credentials.setIsConnected(true);  // 연동 상태 활성화
        credentials.setExternalUserId(String.valueOf(profile.getId()));
        credentials.setExternalUsername(profile.getLoginId());
        credentials.setLastValidatedAt(LocalDateTime.now());

        credentialsRepository.save(credentials);

        log.info("Canvas token registered successfully for cognitoSub: {}, externalUserId: {}, loginId: {}",
                cognitoSub, profile.getId(), profile.getLoginId());

        // 4. SQS 이벤트 발행 (user-token-registered-queue)
        publishUserTokenRegisteredEvent(cognitoSub, profile);

        return RegisterCanvasTokenResponse.builder()
                .success(true)
                .message("Canvas token registered successfully")
                .build();
    }

    /**
     * Canvas 토큰을 조회합니다 (내부 API용 - userId로 조회).
     * Lambda에서 userId로 호출하는 경우 사용
     * 복호화된 토큰을 반환합니다.
     *
     * @param userId 사용자 ID
     * @return 복호화된 Canvas 토큰
     */
    @Transactional(readOnly = true)
    public CanvasTokenResponse getCanvasToken(Long userId) {
        log.info("Retrieving Canvas token for userId: {}", userId);

        // userId로 User 조회 후 cognitoSub 획득
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found for userId: " + userId));

        return getCanvasTokenByCognitoSub(user.getCognitoSub());
    }

    /**
     * Canvas 토큰을 조회합니다 (cognitoSub 기반).
     * 복호화된 토큰을 반환합니다.
     *
     * @param cognitoSub Cognito 사용자 ID
     * @return 복호화된 Canvas 토큰
     */
    @Transactional(readOnly = true)
    public CanvasTokenResponse getCanvasTokenByCognitoSub(String cognitoSub) {
        log.info("Retrieving Canvas token for cognitoSub: {}", cognitoSub);

        Credentials credentials = credentialsRepository
                .findByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS)
                .orElseThrow(() -> new CanvasTokenNotFoundException(
                        "Canvas token not found for cognitoSub: " + cognitoSub));

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
     * @param cognitoSub Cognito 사용자 ID
     */
    @Transactional
    public void deleteCanvasToken(String cognitoSub) {
        log.info("Deleting Canvas token for cognitoSub: {}", cognitoSub);

        if (!credentialsRepository.existsByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS)) {
            throw new CanvasTokenNotFoundException("Canvas token not found for cognitoSub: " + cognitoSub);
        }

        credentialsRepository.deleteByCognitoSubAndProvider(cognitoSub, CredentialProvider.CANVAS);

        log.info("Canvas token deleted successfully for cognitoSub: {}", cognitoSub);

        // TODO: Leader 변경 로직 (Course-Service 호출)
    }

    /**
     * SQS에 사용자 토큰 등록 이벤트를 발행합니다
     *
     * @param cognitoSub Cognito 사용자 ID
     * @param profile Canvas 프로필 정보
     */
    private void publishUserTokenRegisteredEvent(String cognitoSub, CanvasApiClient.CanvasProfile profile) {
        UserTokenRegisteredEvent event = UserTokenRegisteredEvent.builder()
                .cognitoSub(cognitoSub)
                .provider("CANVAS")
                .registeredAt(LocalDateTime.now())
                .externalUserId(String.valueOf(profile.getId()))
                .externalUsername(profile.getLoginId())
                .build();

        sqsPublisher.publish(userTokenRegisteredQueue, event);

        log.info("Published UserTokenRegisteredEvent to SQS for cognitoSub={}", cognitoSub);
    }
}