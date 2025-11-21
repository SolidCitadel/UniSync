package com.unisync.user.auth.service;

import com.unisync.user.auth.dto.AuthResponse;
import com.unisync.user.auth.dto.SignInRequest;
import com.unisync.user.auth.dto.SignUpRequest;
import com.unisync.user.auth.exception.AuthenticationException;
import com.unisync.user.auth.exception.DuplicateUserException;
import com.unisync.user.auth.exception.UserNotFoundException;
import com.unisync.user.common.entity.User;
import com.unisync.user.common.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @Mock
    private CognitoService cognitoService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    private SignUpRequest signUpRequest;
    private SignInRequest signInRequest;
    private User user;
    private AuthResponse authResponse;
    private String cognitoSub;

    @BeforeEach
    void setUp() {
        cognitoSub = "test-cognito-sub-123";

        signUpRequest = SignUpRequest.builder()
                .email("test@example.com")
                .password("Password123!")
                .name("테스트 사용자")
                .build();

        signInRequest = SignInRequest.builder()
                .email("test@example.com")
                .password("Password123!")
                .build();

        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("테스트 사용자")
                .cognitoSub(cognitoSub)
                .isActive(true)
                .build();

        authResponse = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .idToken("id-token")
                .expiresIn(3600)
                .tokenType("Bearer")
                .build();
    }

    @Test
    @DisplayName("회원가입 성공 - Cognito 등록 + DB 저장 + 자동 로그인")
    void signUp_Success() {
        // Given
        given(userRepository.existsByEmail(signUpRequest.getEmail()))
                .willReturn(false);
        given(cognitoService.signUp(signUpRequest))
                .willReturn(cognitoSub);
        willDoNothing().given(cognitoService).confirmSignUp(signUpRequest.getEmail());
        given(userRepository.save(any(User.class)))
                .willReturn(user);
        given(cognitoService.signIn(any(SignInRequest.class)))
                .willReturn(authResponse);

        // When
        AuthResponse response = authService.signUp(signUpRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getCognitoSub()).isEqualTo(cognitoSub);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getName()).isEqualTo("테스트 사용자");

        // 검증
        then(userRepository).should().existsByEmail(signUpRequest.getEmail());
        then(cognitoService).should().signUp(signUpRequest);
        then(cognitoService).should().confirmSignUp(signUpRequest.getEmail());
        then(userRepository).should().save(any(User.class));
        then(cognitoService).should().signIn(any(SignInRequest.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복 (DB에 이미 존재)")
    void signUp_Failure_DuplicateEmail() {
        // Given
        given(userRepository.existsByEmail(signUpRequest.getEmail()))
                .willReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.signUp(signUpRequest))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("이미 존재하는 이메일입니다");

        // Cognito 호출되지 않음
        then(cognitoService).should(never()).signUp(any());
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("로그인 성공 - Cognito 인증 + DB 조회")
    void signIn_Success() {
        // Given
        GetUserResponse cognitoUser = GetUserResponse.builder()
                .username(cognitoSub)
                .build();

        given(cognitoService.signIn(signInRequest))
                .willReturn(authResponse);
        given(cognitoService.getUserInfo(authResponse.getAccessToken()))
                .willReturn(cognitoUser);
        given(userRepository.findByCognitoSub(cognitoSub))
                .willReturn(Optional.of(user));

        // When
        AuthResponse response = authService.signIn(signInRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getCognitoSub()).isEqualTo(cognitoSub);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getName()).isEqualTo("테스트 사용자");

        then(cognitoService).should().signIn(signInRequest);
        then(cognitoService).should().getUserInfo(authResponse.getAccessToken());
        then(userRepository).should().findByCognitoSub(cognitoSub);
    }

    @Test
    @DisplayName("로그인 실패 - DB에 사용자 없음 (Cognito는 성공)")
    void signIn_Failure_UserNotFound() {
        // Given
        GetUserResponse cognitoUser = GetUserResponse.builder()
                .username(cognitoSub)
                .build();

        given(cognitoService.signIn(signInRequest))
                .willReturn(authResponse);
        given(cognitoService.getUserInfo(authResponse.getAccessToken()))
                .willReturn(cognitoUser);
        given(userRepository.findByCognitoSub(cognitoSub))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.signIn(signInRequest))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("로그인 실패 - 비활성화된 사용자")
    void signIn_Failure_InactiveUser() {
        // Given
        User inactiveUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("테스트 사용자")
                .cognitoSub(cognitoSub)
                .isActive(false) // 비활성화
                .build();

        GetUserResponse cognitoUser = GetUserResponse.builder()
                .username(cognitoSub)
                .build();

        given(cognitoService.signIn(signInRequest))
                .willReturn(authResponse);
        given(cognitoService.getUserInfo(authResponse.getAccessToken()))
                .willReturn(cognitoUser);
        given(userRepository.findByCognitoSub(cognitoSub))
                .willReturn(Optional.of(inactiveUser));

        // When & Then
        assertThatThrownBy(() -> authService.signIn(signInRequest))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("비활성화된 사용자입니다");
    }

    @Test
    @DisplayName("Access Token으로 사용자 조회 성공")
    void getUserByAccessToken_Success() {
        // Given
        String accessToken = "valid-access-token";

        GetUserResponse cognitoUser = GetUserResponse.builder()
                .username(cognitoSub)
                .build();

        given(cognitoService.getUserInfo(accessToken))
                .willReturn(cognitoUser);
        given(userRepository.findByCognitoSub(cognitoSub))
                .willReturn(Optional.of(user));

        // When
        User result = authService.getUserByAccessToken(accessToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCognitoSub()).isEqualTo(cognitoSub);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getName()).isEqualTo("테스트 사용자");

        then(cognitoService).should().getUserInfo(accessToken);
        then(userRepository).should().findByCognitoSub(cognitoSub);
    }

    @Test
    @DisplayName("Access Token으로 사용자 조회 실패 - 사용자 없음")
    void getUserByAccessToken_Failure_UserNotFound() {
        // Given
        String accessToken = "valid-access-token";

        GetUserResponse cognitoUser = GetUserResponse.builder()
                .username(cognitoSub)
                .build();

        given(cognitoService.getUserInfo(accessToken))
                .willReturn(cognitoUser);
        given(userRepository.findByCognitoSub(cognitoSub))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.getUserByAccessToken(accessToken))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("회원가입 시 DB 저장되는 User 엔티티 검증")
    void signUp_VerifyUserEntity() {
        // Given
        given(userRepository.existsByEmail(signUpRequest.getEmail()))
                .willReturn(false);
        given(cognitoService.signUp(signUpRequest))
                .willReturn(cognitoSub);
        willDoNothing().given(cognitoService).confirmSignUp(anyString());
        given(userRepository.save(any(User.class)))
                .willReturn(user);
        given(cognitoService.signIn(any(SignInRequest.class)))
                .willReturn(authResponse);

        // When
        authService.signUp(signUpRequest);

        // Then - User 엔티티 검증
        then(userRepository).should().save(argThat(savedUser ->
                savedUser.getEmail().equals("test@example.com") &&
                        savedUser.getName().equals("테스트 사용자") &&
                        savedUser.getCognitoSub().equals(cognitoSub) &&
                        savedUser.getIsActive()
        ));
    }
}
