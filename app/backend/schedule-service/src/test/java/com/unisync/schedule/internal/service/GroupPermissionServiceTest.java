package com.unisync.schedule.internal.service;

import com.unisync.schedule.common.exception.UnauthorizedAccessException;
import com.unisync.schedule.internal.client.UserServiceClient;
import com.unisync.schedule.internal.dto.GroupMembershipResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupPermissionService 단위 테스트")
class GroupPermissionServiceTest {

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private GroupPermissionService groupPermissionService;

    private static final Long GROUP_ID = 1L;
    private static final String COGNITO_SUB = "test-cognito-sub";

    @Nested
    @DisplayName("읽기 권한 검증")
    class ValidateReadPermission {

        @Test
        @DisplayName("groupId가 null이면 검증 스킵")
        void test_validateReadPermission_NullGroupId_ShouldSkip() {
            // when & then (예외 없이 통과)
            groupPermissionService.validateReadPermission(null, COGNITO_SUB);

            then(userServiceClient).should(never()).getMembership(null, COGNITO_SUB);
        }

        @Test
        @DisplayName("멤버인 경우 통과")
        void test_validateReadPermission_Member_ShouldPass() {
            // given
            GroupMembershipResponse response = GroupMembershipResponse.builder()
                    .groupId(GROUP_ID)
                    .cognitoSub(COGNITO_SUB)
                    .isMember(true)
                    .role("MEMBER")
                    .build();

            given(userServiceClient.getMembership(GROUP_ID, COGNITO_SUB)).willReturn(response);

            // when & then (예외 없이 통과)
            groupPermissionService.validateReadPermission(GROUP_ID, COGNITO_SUB);
        }

        @Test
        @DisplayName("멤버가 아닌 경우 예외 발생")
        void test_validateReadPermission_NotMember_ShouldThrowException() {
            // given
            GroupMembershipResponse response = GroupMembershipResponse.builder()
                    .groupId(GROUP_ID)
                    .cognitoSub(COGNITO_SUB)
                    .isMember(false)
                    .role(null)
                    .build();

            given(userServiceClient.getMembership(GROUP_ID, COGNITO_SUB)).willReturn(response);

            // when & then
            assertThatThrownBy(() -> groupPermissionService.validateReadPermission(GROUP_ID, COGNITO_SUB))
                    .isInstanceOf(UnauthorizedAccessException.class);
        }
    }

    @Nested
    @DisplayName("쓰기 권한 검증")
    class ValidateWritePermission {

        @Test
        @DisplayName("groupId가 null이면 검증 스킵")
        void test_validateWritePermission_NullGroupId_ShouldSkip() {
            // when & then (예외 없이 통과)
            groupPermissionService.validateWritePermission(null, COGNITO_SUB);

            then(userServiceClient).should(never()).getMembership(null, COGNITO_SUB);
        }

        @Test
        @DisplayName("OWNER인 경우 통과")
        void test_validateWritePermission_Owner_ShouldPass() {
            // given
            GroupMembershipResponse response = GroupMembershipResponse.builder()
                    .groupId(GROUP_ID)
                    .cognitoSub(COGNITO_SUB)
                    .isMember(true)
                    .role("OWNER")
                    .build();

            given(userServiceClient.getMembership(GROUP_ID, COGNITO_SUB)).willReturn(response);

            // when & then (예외 없이 통과)
            groupPermissionService.validateWritePermission(GROUP_ID, COGNITO_SUB);
        }

        @Test
        @DisplayName("ADMIN인 경우 통과")
        void test_validateWritePermission_Admin_ShouldPass() {
            // given
            GroupMembershipResponse response = GroupMembershipResponse.builder()
                    .groupId(GROUP_ID)
                    .cognitoSub(COGNITO_SUB)
                    .isMember(true)
                    .role("ADMIN")
                    .build();

            given(userServiceClient.getMembership(GROUP_ID, COGNITO_SUB)).willReturn(response);

            // when & then (예외 없이 통과)
            groupPermissionService.validateWritePermission(GROUP_ID, COGNITO_SUB);
        }

        @Test
        @DisplayName("MEMBER인 경우 예외 발생")
        void test_validateWritePermission_Member_ShouldThrowException() {
            // given
            GroupMembershipResponse response = GroupMembershipResponse.builder()
                    .groupId(GROUP_ID)
                    .cognitoSub(COGNITO_SUB)
                    .isMember(true)
                    .role("MEMBER")
                    .build();

            given(userServiceClient.getMembership(GROUP_ID, COGNITO_SUB)).willReturn(response);

            // when & then
            assertThatThrownBy(() -> groupPermissionService.validateWritePermission(GROUP_ID, COGNITO_SUB))
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessageContaining("OWNER 또는 ADMIN");
        }

        @Test
        @DisplayName("멤버가 아닌 경우 예외 발생")
        void test_validateWritePermission_NotMember_ShouldThrowException() {
            // given
            GroupMembershipResponse response = GroupMembershipResponse.builder()
                    .groupId(GROUP_ID)
                    .cognitoSub(COGNITO_SUB)
                    .isMember(false)
                    .role(null)
                    .build();

            given(userServiceClient.getMembership(GROUP_ID, COGNITO_SUB)).willReturn(response);

            // when & then
            assertThatThrownBy(() -> groupPermissionService.validateWritePermission(GROUP_ID, COGNITO_SUB))
                    .isInstanceOf(UnauthorizedAccessException.class);
        }
    }

    @Nested
    @DisplayName("멤버십 조회")
    class GetMembership {

        @Test
        @DisplayName("멤버십 조회 결과 반환")
        void test_getMembership_ShouldReturnResponse() {
            // given
            GroupMembershipResponse expected = GroupMembershipResponse.builder()
                    .groupId(GROUP_ID)
                    .cognitoSub(COGNITO_SUB)
                    .isMember(true)
                    .role("ADMIN")
                    .build();

            given(userServiceClient.getMembership(GROUP_ID, COGNITO_SUB)).willReturn(expected);

            // when
            GroupMembershipResponse actual = groupPermissionService.getMembership(GROUP_ID, COGNITO_SUB);

            // then
            assertThat(actual).isEqualTo(expected);
        }
    }
}
