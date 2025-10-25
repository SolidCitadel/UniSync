package com.unisync.user.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisync.user.auth.dto.AuthResponse;
import com.unisync.user.auth.dto.SignInRequest;
import com.unisync.user.auth.dto.SignUpRequest;
import com.unisync.user.auth.exception.DuplicateUserException;
import com.unisync.user.auth.exception.InvalidCredentialsException;
import com.unisync.user.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 단위 테스트
 *
 * MockMvc를 사용하여 실제 HTTP 요청 없이 컨트롤러 레이어만 테스트합니다.
 * AuthService는 Mock으로 대체하여 비즈니스 로직은 테스트하지 않습니다.
 */
@WebMvcTest(AuthController.class)
@DisplayName("AuthController 단위 테스트")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("회원가입 성공")
    void signUp_Success() throws Exception {
        // Given
        SignUpRequest request = SignUpRequest.builder()
                .email("test@example.com")
                .password("Test1234!")
                .name("테스트 유저")
                .build();

        AuthResponse response = AuthResponse.builder()
                .userId(1L)
                .email("test@example.com")
                .name("테스트 유저")
                .message("회원가입이 완료되었습니다")
                .build();

        when(authService.signUp(any(SignUpRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("테스트 유저"))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다"));
    }

    @Test
    @DisplayName("회원가입 실패 - 중복 이메일")
    void signUp_Fail_DuplicateEmail() throws Exception {
        // Given
        SignUpRequest request = SignUpRequest.builder()
                .email("duplicate@example.com")
                .password("Test1234!")
                .name("테스트 유저")
                .build();

        when(authService.signUp(any(SignUpRequest.class)))
                .thenThrow(new DuplicateUserException("이미 존재하는 이메일입니다"));

        // When & Then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_USER"))
                .andExpect(jsonPath("$.message").value("이미 존재하는 이메일입니다"));
    }

    @Test
    @DisplayName("로그인 성공")
    void signIn_Success() throws Exception {
        // Given
        SignInRequest request = SignInRequest.builder()
                .email("test@example.com")
                .password("Test1234!")
                .build();

        AuthResponse response = AuthResponse.builder()
                .userId(1L)
                .email("test@example.com")
                .name("테스트 유저")
                .accessToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                .refreshToken("refresh_token_here")
                .expiresIn(3600)
                .message("로그인 성공")
                .build();

        when(authService.signIn(any(SignInRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("로그인 실패 - 잘못된 비밀번호")
    void signIn_Fail_InvalidPassword() throws Exception {
        // Given
        SignInRequest request = SignInRequest.builder()
                .email("test@example.com")
                .password("WrongPassword!")
                .build();

        when(authService.signIn(any(SignInRequest.class)))
                .thenThrow(new InvalidCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다"));

        // When & Then
        mockMvc.perform(post("/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 일치하지 않습니다"));
    }

    @Test
    @DisplayName("헬스체크 성공")
    void healthCheck_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/auth/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("user-service"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
