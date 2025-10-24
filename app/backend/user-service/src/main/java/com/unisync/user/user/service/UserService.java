package com.unisync.user.user.service;

import com.unisync.user.common.entity.User;
import com.unisync.user.common.repository.UserRepository;
import com.unisync.user.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * cognitoSub으로 사용자 정보 조회
     * API Gateway에서 X-Cognito-Sub 헤더로 전달받는 경우
     *
     * API Gateway가 JWT를 검증하고 cognitoSub를 추출해서 전달했으므로, 이미 인증된 사용자입니다.
     *
     * @param cognitoSub Cognito User Pool의 sub (UUID) 값
     * @return 사용자 정보
     */
    public UserResponse getUserByCognitoSub(String cognitoSub) {
        log.info("사용자 정보 조회 요청: cognitoSub={}", cognitoSub);

        User user = userRepository.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + cognitoSub));

        return UserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .cognitoSub(user.getCognitoSub())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}