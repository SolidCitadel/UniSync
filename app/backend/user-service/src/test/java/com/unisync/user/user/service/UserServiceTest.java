package com.unisync.user.user.service;

import com.unisync.user.auth.exception.UserNotFoundException;
import com.unisync.user.common.entity.User;
import com.unisync.user.common.repository.UserRepository;
import com.unisync.user.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private String cognitoSub;
    private User user;

    @BeforeEach
    void setUp() {
        cognitoSub = "test-cognito-sub-123";

        user = User.builder()
                .id(1L)
                .cognitoSub(cognitoSub)
                .email("test@example.com")
                .name("Test User")
                .isActive(true)
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("사용자 정보 조회 성공")
    void getUserByCognitoSub_Success() {
        // given
        given(userRepository.findByCognitoSub(cognitoSub))
                .willReturn(Optional.of(user));

        // when
        UserResponse response = userService.getUserByCognitoSub(cognitoSub);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getName()).isEqualTo("Test User");
        assertThat(response.getCognitoSub()).isEqualTo(cognitoSub);
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();

        then(userRepository).should().findByCognitoSub(cognitoSub);
    }

    @Test
    @DisplayName("사용자 정보 조회 실패 - 사용자 없음")
    void getUserByCognitoSub_Failure_NotFound() {
        // given
        given(userRepository.findByCognitoSub(cognitoSub))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUserByCognitoSub(cognitoSub))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        then(userRepository).should().findByCognitoSub(cognitoSub);
    }

    @Test
    @DisplayName("사용자 정보 조회 성공 - 비활성화된 사용자")
    void getUserByCognitoSub_Success_InactiveUser() {
        // given
        user.setIsActive(false);
        given(userRepository.findByCognitoSub(cognitoSub))
                .willReturn(Optional.of(user));

        // when
        UserResponse response = userService.getUserByCognitoSub(cognitoSub);

        // then
        assertThat(response.getIsActive()).isFalse();
        then(userRepository).should().findByCognitoSub(cognitoSub);
    }
}
