package com.unisync.user.user.controller;

import com.unisync.user.auth.exception.UserNotFoundException;
import com.unisync.user.user.dto.UserResponse;
import com.unisync.user.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 단위 테스트 - API Gateway 헤더 시뮬레이션
 *
 * 이 테스트는 API Gateway를 거쳐서 들어오는 요청을 시뮬레이션합니다.
 * API Gateway가 JWT를 검증하고 추가하는 헤더(X-Cognito-Sub, X-User-Email 등)를
 * MockMvc의 .header() 메서드로 직접 주입합니다.
 *
 * 실제 환경에서는:
 * 1. 클라이언트가 Authorization: Bearer {JWT} 헤더로 요청
 * 2. API Gateway가 JWT 검증
 * 3. API Gateway가 X-Cognito-Sub, X-User-Email 등의 헤더 추가
 * 4. 백엔드 서비스(User Service)로 전달
 *
 * 단위 테스트에서는:
 * 1. MockMvc로 요청 생성
 * 2. .header()로 API Gateway가 추가할 헤더를 직접 설정
 * 3. 컨트롤러 테스트
 */
@WebMvcTest(UserController.class)
@DisplayName("UserController 단위 테스트 - API Gateway 헤더 시뮬레이션")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("내 정보 조회 성공 - API Gateway 헤더 사용")
    void getMyInfo_Success_WithGatewayHeaders() throws Exception {
        // Given - API Gateway가 추가할 헤더 준비
        String cognitoSub = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String userEmail = "test@example.com";
        String userName = "테스트 유저";

        UserResponse response = UserResponse.builder()
                .userId(123L)
                .email(userEmail)
                .name(userName)
                .cognitoSub(cognitoSub)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userService.getUserByCognitoSub(anyString())).thenReturn(response);

        // When & Then - API Gateway가 추가하는 헤더를 MockMvc로 시뮬레이션
        mockMvc.perform(get("/users/me")
                        // API Gateway가 JWT를 파싱해서 추가하는 헤더들
                        .header("X-Cognito-Sub", cognitoSub)
                        .header("X-User-Email", userEmail)
                        .header("X-User-Name", userName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(123L))
                .andExpect(jsonPath("$.email").value(userEmail))
                .andExpect(jsonPath("$.name").value(userName))
                .andExpect(jsonPath("$.cognitoSub").value(cognitoSub))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    // 실제 환경에서는 API Gateway가 항상 X-Cognito-Sub 헤더를 추가하므로,
    // 헤더 없음 케이스는 테스트하지 않습니다.

    @Test
    @DisplayName("내 정보 조회 실패 - 존재하지 않는 사용자")
    void getMyInfo_Fail_UserNotFound() throws Exception {
        // Given
        String cognitoSub = "nonexistent-sub";

        when(userService.getUserByCognitoSub(anyString()))
                .thenThrow(new UserNotFoundException("사용자를 찾을 수 없습니다: " + cognitoSub));

        // When & Then - GlobalExceptionHandler가 처리
        mockMvc.perform(get("/users/me")
                        .header("X-Cognito-Sub", cognitoSub)
                        .header("X-User-Email", "test@example.com")
                        .header("X-User-Name", "테스트 유저"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다: " + cognitoSub))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    /**
     * 여러 헤더를 동시에 테스트하는 경우
     * 실제 API Gateway는 모든 헤더를 함께 전달합니다.
     */
    @Test
    @DisplayName("모든 Gateway 헤더 포함 - 통합 시나리오")
    void getMyInfo_WithAllGatewayHeaders() throws Exception {
        // Given
        String cognitoSub = "full-cognito-sub-789";
        String userEmail = "full@example.com";
        String userName = "Full Test User";

        UserResponse response = UserResponse.builder()
                .userId(789L)
                .email(userEmail)
                .name(userName)
                .cognitoSub(cognitoSub)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userService.getUserByCognitoSub(anyString())).thenReturn(response);

        // When & Then - API Gateway가 전달하는 모든 헤더 포함
        mockMvc.perform(get("/users/me")
                        .header("X-Cognito-Sub", cognitoSub)
                        .header("X-User-Email", userEmail)
                        .header("X-User-Name", userName)
                        // 실제로는 Authorization 헤더도 함께 전달되지만,
                        // 백엔드 서비스에서는 사용하지 않으므로 생략 가능
                        .header("Authorization", "Bearer eyJhbGc..."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(789L))
                .andExpect(jsonPath("$.email").value(userEmail))
                .andExpect(jsonPath("$.name").value(userName))
                .andExpect(jsonPath("$.cognitoSub").value(cognitoSub));
    }
}