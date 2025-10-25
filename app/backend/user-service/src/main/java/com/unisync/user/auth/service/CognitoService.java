package com.unisync.user.auth.service;

import com.unisync.user.auth.exception.AuthenticationException;
import com.unisync.user.auth.exception.DuplicateUserException;
import com.unisync.user.auth.exception.InvalidCredentialsException;
import com.unisync.user.common.config.AwsCognitoConfig;
import com.unisync.user.auth.dto.AuthResponse;
import com.unisync.user.auth.dto.SignInRequest;
import com.unisync.user.auth.dto.SignUpRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CognitoService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final AwsCognitoConfig cognitoConfig;

    /**
     * Cognito에 사용자 회원가입
     */
    public String signUp(SignUpRequest request) {
        try {
            // 사용자 속성 설정
            AttributeType emailAttr = AttributeType.builder()
                    .name("email")
                    .value(request.getEmail())
                    .build();

            AttributeType nameAttr = AttributeType.builder()
                    .name("name")
                    .value(request.getName())
                    .build();

            // SignUp 요청
            software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest cognitoRequest =
                    software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest.builder()
                    .clientId(cognitoConfig.getClientId())
                    .username(request.getEmail())
                    .password(request.getPassword())
                    .userAttributes(emailAttr, nameAttr)
                    .build();

            SignUpResponse response = cognitoClient.signUp(cognitoRequest);

            log.info("Cognito 회원가입 성공: {}", response.userSub());

            // Cognito User Sub (고유 ID) 반환
            return response.userSub();

        } catch (UsernameExistsException e) {
            log.error("이미 존재하는 이메일: {}", request.getEmail());
            throw new DuplicateUserException("이미 존재하는 이메일입니다: " + request.getEmail());
        } catch (InvalidPasswordException e) {
            log.error("비밀번호 정책 위반: {}", e.getMessage());
            throw new AuthenticationException("비밀번호는 최소 8자 이상이며, 대문자, 소문자, 숫자를 포함해야 합니다");
        } catch (CognitoIdentityProviderException e) {
            log.error("Cognito 회원가입 실패: {}", e.getMessage());
            throw new AuthenticationException("회원가입에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * Cognito 로그인 (InitiateAuth)
     */
    public AuthResponse signIn(SignInRequest request) {
        try {
            Map<String, String> authParams = new HashMap<>();
            authParams.put("USERNAME", request.getEmail());
            authParams.put("PASSWORD", request.getPassword());

            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                    .clientId(cognitoConfig.getClientId())
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .authParameters(authParams)
                    .build();

            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);

            AuthenticationResultType result = authResponse.authenticationResult();

            if (result == null) {
                throw new AuthenticationException("인증 결과를 받지 못했습니다");
            }

            log.info("Cognito 로그인 성공: {}", request.getEmail());

            // 토큰 정보 반환
            return AuthResponse.builder()
                    .accessToken(result.accessToken())
                    .refreshToken(result.refreshToken())
                    .idToken(result.idToken())
                    .expiresIn(result.expiresIn())
                    .tokenType(result.tokenType())
                    .build();

        } catch (NotAuthorizedException e) {
            log.error("인증 실패: {}", e.getMessage());
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다");
        } catch (UserNotConfirmedException e) {
            log.error("이메일 미인증 사용자: {}", request.getEmail());
            throw new AuthenticationException("이메일 인증이 필요합니다");
        } catch (CognitoIdentityProviderException e) {
            log.error("Cognito 로그인 실패: {}", e.getMessage());
            throw new AuthenticationException("로그인에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * Access Token에서 사용자 정보 추출
     */
    public GetUserResponse getUserInfo(String accessToken) {
        try {
            GetUserRequest request = GetUserRequest.builder()
                    .accessToken(accessToken)
                    .build();

            return cognitoClient.getUser(request);
        } catch (CognitoIdentityProviderException e) {
            log.error("사용자 정보 조회 실패: {}", e.getMessage());
            throw new AuthenticationException("사용자 정보 조회에 실패했습니다");
        }
    }

    /**
     * 이메일 자동 인증 (LocalStack 개발 환경용)
     */
    public void confirmSignUp(String email) {
        try {
            AdminConfirmSignUpRequest request = AdminConfirmSignUpRequest.builder()
                    .userPoolId(cognitoConfig.getUserPoolId())
                    .username(email)
                    .build();

            cognitoClient.adminConfirmSignUp(request);
            log.info("이메일 자동 인증 완료: {}", email);
        } catch (CognitoIdentityProviderException e) {
            log.error("이메일 인증 실패: {}", e.getMessage());
        }
    }
}
