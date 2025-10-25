package com.unisync.user.auth.service;

import com.unisync.user.auth.dto.AuthResponse;
import com.unisync.user.auth.dto.SignInRequest;
import com.unisync.user.auth.dto.SignUpRequest;
import com.unisync.user.auth.exception.AuthenticationException;
import com.unisync.user.auth.exception.DuplicateUserException;
import com.unisync.user.auth.exception.UserNotFoundException;
import com.unisync.user.common.entity.User;
import com.unisync.user.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final CognitoService cognitoService;
    private final UserRepository userRepository;

    /**
     * 회원가입: Cognito 등록 + DB 저장
     */
    @Transactional
    public AuthResponse signUp(SignUpRequest request) {
        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateUserException("이미 존재하는 이메일입니다: " + request.getEmail());
        }

        // 1. Cognito에 사용자 등록
        String cognitoSub = cognitoService.signUp(request);

        // 2. LocalStack 환경에서는 자동으로 이메일 인증 처리
        cognitoService.confirmSignUp(request.getEmail());

        // 3. DB에 사용자 정보 저장
        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .cognitoSub(cognitoSub)
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("사용자 DB 저장 완료: userId={}, email={}", user.getId(), user.getEmail());

        // 4. 자동 로그인 처리
        SignInRequest signInRequest = new SignInRequest();
        signInRequest.setEmail(request.getEmail());
        signInRequest.setPassword(request.getPassword());

        AuthResponse authResponse = cognitoService.signIn(signInRequest);

        // 사용자 정보 추가
        authResponse.setUserId(user.getId());
        authResponse.setEmail(user.getEmail());
        authResponse.setName(user.getName());

        return authResponse;
    }

    /**
     * 로그인: Cognito 인증 + DB 조회
     */
    @Transactional(readOnly = true)
    public AuthResponse signIn(SignInRequest request) {
        // 1. Cognito 인증
        AuthResponse authResponse = cognitoService.signIn(request);

        // 2. Access Token으로 사용자 정보 조회
        GetUserResponse cognitoUser = cognitoService.getUserInfo(authResponse.getAccessToken());
        String cognitoSub = cognitoUser.username();

        // 3. DB에서 사용자 조회
        User user = userRepository.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        if (!user.getIsActive()) {
            throw new AuthenticationException("비활성화된 사용자입니다");
        }

        // 4. 사용자 정보 추가
        authResponse.setUserId(user.getId());
        authResponse.setEmail(user.getEmail());
        authResponse.setName(user.getName());

        log.info("로그인 성공: userId={}, email={}", user.getId(), user.getEmail());

        return authResponse;
    }

    /**
     * Access Token으로 사용자 정보 조회
     */
    @Transactional(readOnly = true)
    public User getUserByAccessToken(String accessToken) {
        GetUserResponse cognitoUser = cognitoService.getUserInfo(accessToken);
        String cognitoSub = cognitoUser.username();

        return userRepository.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));
    }
}
