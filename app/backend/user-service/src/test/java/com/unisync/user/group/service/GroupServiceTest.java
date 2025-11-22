package com.unisync.user.group.service;

import com.unisync.user.auth.exception.UserNotFoundException;
import com.unisync.user.common.client.ScheduleServiceClient;
import com.unisync.user.common.entity.Group;
import com.unisync.user.common.entity.GroupMember;
import com.unisync.user.common.entity.GroupRole;
import com.unisync.user.common.entity.User;
import com.unisync.user.common.repository.GroupMemberRepository;
import com.unisync.user.common.repository.GroupRepository;
import com.unisync.user.common.repository.UserRepository;
import com.unisync.user.friend.dto.MessageResponse;
import com.unisync.user.group.dto.*;
import com.unisync.user.group.exception.GroupNotFoundException;
import com.unisync.user.group.exception.InsufficientPermissionException;
import com.unisync.user.group.exception.MemberAlreadyExistsException;
import com.unisync.user.group.exception.MemberNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupService 단위 테스트")
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ScheduleServiceClient scheduleServiceClient;

    @InjectMocks
    private GroupService groupService;

    private User owner;
    private User member;
    private Group group;
    private GroupMember ownerMember;
    private GroupMember regularMember;
    private String ownerCognitoSub;
    private String memberCognitoSub;

    @BeforeEach
    void setUp() {
        ownerCognitoSub = "owner-cognito-sub-123";
        memberCognitoSub = "member-cognito-sub-456";

        owner = User.builder()
                .id(1L)
                .email("owner@example.com")
                .name("소유자")
                .cognitoSub(ownerCognitoSub)
                .isActive(true)
                .build();

        member = User.builder()
                .id(2L)
                .email("member@example.com")
                .name("멤버")
                .cognitoSub(memberCognitoSub)
                .isActive(true)
                .build();

        group = Group.builder()
                .id(1L)
                .name("팀 프로젝트")
                .description("소프트웨어 공학 팀 프로젝트")
                .ownerCognitoSub(ownerCognitoSub)
                .build();

        ownerMember = GroupMember.builder()
                .id(1L)
                .groupId(group.getId())
                .userCognitoSub(ownerCognitoSub)
                .role(GroupRole.OWNER)
                .build();

        regularMember = GroupMember.builder()
                .id(2L)
                .groupId(group.getId())
                .userCognitoSub(memberCognitoSub)
                .role(GroupRole.MEMBER)
                .build();
    }

    @Test
    @DisplayName("그룹 생성 성공 - 소유자 자동 OWNER로 등록")
    void test_createGroup_Success_ShouldRegisterOwnerAsMember() {
        // given
        GroupCreateRequest request = GroupCreateRequest.builder()
                .name("새 그룹")
                .description("그룹 설명")
                .build();

        given(userRepository.findByCognitoSub(ownerCognitoSub))
                .willReturn(Optional.of(owner));
        given(groupRepository.save(any(Group.class)))
                .willAnswer(invocation -> {
                    Group g = invocation.getArgument(0);
                    g.setId(1L);
                    return g;
                });
        given(groupMemberRepository.save(any(GroupMember.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        GroupResponse response = groupService.createGroup(ownerCognitoSub, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("새 그룹");
        assertThat(response.getMyRole()).isEqualTo(GroupRole.OWNER);

        // Group 저장 + GroupMember 저장
        then(groupRepository).should(times(1)).save(any(Group.class));
        then(groupMemberRepository).should(times(1)).save(any(GroupMember.class));
    }

    @Test
    @DisplayName("내가 속한 그룹 목록 조회")
    void test_getMyGroups_Success_ShouldReturnGroupList() {
        // given
        given(groupMemberRepository.findByUserCognitoSub(ownerCognitoSub))
                .willReturn(List.of(ownerMember));
        given(groupRepository.findById(group.getId()))
                .willReturn(Optional.of(group));
        given(userRepository.findByCognitoSub(ownerCognitoSub))
                .willReturn(Optional.of(owner));
        given(groupMemberRepository.countByGroupId(group.getId()))
                .willReturn(2L);

        // when
        List<GroupResponse> groups = groupService.getMyGroups(ownerCognitoSub);

        // then
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getGroupId()).isEqualTo(group.getId());
        assertThat(groups.get(0).getMyRole()).isEqualTo(GroupRole.OWNER);
        assertThat(groups.get(0).getMemberCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("그룹 상세 조회 - 멤버 목록 포함")
    void test_getGroupDetails_Success_ShouldReturnMemberList() {
        // given
        given(groupMemberRepository.existsByGroupIdAndUserCognitoSub(group.getId(), ownerCognitoSub))
                .willReturn(true);
        given(groupRepository.findById(group.getId()))
                .willReturn(Optional.of(group));
        given(userRepository.findByCognitoSub(ownerCognitoSub))
                .willReturn(Optional.of(owner));
        given(groupMemberRepository.findByGroupId(group.getId()))
                .willReturn(List.of(ownerMember, regularMember));
        given(userRepository.findByCognitoSub(memberCognitoSub))
                .willReturn(Optional.of(member));

        // when
        GroupDetailResponse response = groupService.getGroupDetails(ownerCognitoSub, group.getId());

        // then
        assertThat(response).isNotNull();
        assertThat(response.getGroupId()).isEqualTo(group.getId());
        assertThat(response.getMembers()).hasSize(2);
    }

    @Test
    @DisplayName("그룹 수정 성공 - OWNER만 가능")
    void test_updateGroup_Success_ShouldUpdateGroupInfo() {
        // given
        GroupUpdateRequest request = GroupUpdateRequest.builder()
                .name("수정된 그룹명")
                .description("수정된 설명")
                .build();

        given(userRepository.findByCognitoSub(ownerCognitoSub))
                .willReturn(Optional.of(owner));
        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), ownerCognitoSub))
                .willReturn(Optional.of(ownerMember));
        given(groupRepository.findById(group.getId()))
                .willReturn(Optional.of(group));
        given(groupRepository.save(any(Group.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(groupMemberRepository.countByGroupId(group.getId()))
                .willReturn(2L);

        // when
        GroupResponse response = groupService.updateGroup(ownerCognitoSub, group.getId(), request);

        // then
        assertThat(response.getName()).isEqualTo("수정된 그룹명");
        then(groupRepository).should(times(1)).save(any(Group.class));
    }

    @Test
    @DisplayName("그룹 수정 실패 - OWNER 권한 없음")
    void test_updateGroup_InsufficientPermission_ShouldThrowException() {
        // given
        GroupUpdateRequest request = GroupUpdateRequest.builder()
                .name("수정된 그룹명")
                .build();

        given(userRepository.findByCognitoSub(memberCognitoSub))
                .willReturn(Optional.of(member));
        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), memberCognitoSub))
                .willReturn(Optional.of(regularMember));

        // when & then
        assertThatThrownBy(() -> groupService.updateGroup(memberCognitoSub, group.getId(), request))
                .isInstanceOf(InsufficientPermissionException.class);
    }

    @Test
    @DisplayName("그룹 삭제 성공 - OWNER만 가능, Schedule-Service 호출")
    void test_deleteGroup_Success_ShouldDeleteGroupAndCallScheduleService() {
        // given
        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), ownerCognitoSub))
                .willReturn(Optional.of(ownerMember));
        given(groupRepository.findById(group.getId()))
                .willReturn(Optional.of(group));
        given(scheduleServiceClient.deleteGroupData(group.getId()))
                .willReturn(true);

        // when
        MessageResponse response = groupService.deleteGroup(ownerCognitoSub, group.getId());

        // then
        assertThat(response.getMessage()).contains("삭제");
        then(scheduleServiceClient).should(times(1)).deleteGroupData(group.getId());
        then(groupMemberRepository).should(times(1)).deleteByGroupId(group.getId());
        then(groupRepository).should(times(1)).delete(group);
    }

    @Test
    @DisplayName("멤버 초대 성공 - OWNER/ADMIN 가능")
    void test_inviteMember_Success_ShouldAddNewMember() {
        // given
        MemberInviteRequest request = MemberInviteRequest.builder()
                .userCognitoSub(memberCognitoSub)
                .role(GroupRole.MEMBER)
                .build();

        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), ownerCognitoSub))
                .willReturn(Optional.of(ownerMember));
        given(groupRepository.findById(group.getId()))
                .willReturn(Optional.of(group));
        given(userRepository.findByCognitoSub(memberCognitoSub))
                .willReturn(Optional.of(member));
        given(groupMemberRepository.existsByGroupIdAndUserCognitoSub(group.getId(), memberCognitoSub))
                .willReturn(false);
        given(groupMemberRepository.save(any(GroupMember.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        MemberResponse response = groupService.inviteMember(ownerCognitoSub, group.getId(), request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getUser().getCognitoSub()).isEqualTo(memberCognitoSub);
        assertThat(response.getRole()).isEqualTo(GroupRole.MEMBER);

        then(groupMemberRepository).should(times(1)).save(any(GroupMember.class));
    }

    @Test
    @DisplayName("멤버 초대 실패 - 이미 멤버임")
    void test_inviteMember_AlreadyExists_ShouldThrowException() {
        // given
        MemberInviteRequest request = MemberInviteRequest.builder()
                .userCognitoSub(memberCognitoSub)
                .role(GroupRole.MEMBER)
                .build();

        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), ownerCognitoSub))
                .willReturn(Optional.of(ownerMember));
        given(groupRepository.findById(group.getId()))
                .willReturn(Optional.of(group));
        given(userRepository.findByCognitoSub(memberCognitoSub))
                .willReturn(Optional.of(member));
        given(groupMemberRepository.existsByGroupIdAndUserCognitoSub(group.getId(), memberCognitoSub))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> groupService.inviteMember(ownerCognitoSub, group.getId(), request))
                .isInstanceOf(MemberAlreadyExistsException.class);
    }

    @Test
    @DisplayName("멤버 역할 변경 성공 - OWNER만 가능")
    void test_updateMemberRole_Success_ShouldUpdateRole() {
        // given
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .role(GroupRole.ADMIN)
                .build();

        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), ownerCognitoSub))
                .willReturn(Optional.of(ownerMember));
        given(groupMemberRepository.findById(regularMember.getId()))
                .willReturn(Optional.of(regularMember));
        given(groupMemberRepository.save(any(GroupMember.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(userRepository.findByCognitoSub(memberCognitoSub))
                .willReturn(Optional.of(member));

        // when
        MemberResponse response = groupService.updateMemberRole(
                ownerCognitoSub, group.getId(), regularMember.getId(), request);

        // then
        assertThat(response.getRole()).isEqualTo(GroupRole.ADMIN);
        then(groupMemberRepository).should(times(1)).save(any(GroupMember.class));
    }

    @Test
    @DisplayName("멤버 역할 변경 실패 - OWNER로 변경 불가")
    void test_updateMemberRole_CannotChangeToOwner_ShouldThrowException() {
        // given
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .role(GroupRole.OWNER)
                .build();

        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), ownerCognitoSub))
                .willReturn(Optional.of(ownerMember));

        // when & then
        assertThatThrownBy(() -> groupService.updateMemberRole(
                ownerCognitoSub, group.getId(), regularMember.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OWNER");
    }

    @Test
    @DisplayName("멤버 제거 성공 - OWNER는 모든 멤버 제거 가능")
    void test_removeMember_ByOwner_Success_ShouldRemoveMember() {
        // given
        given(groupMemberRepository.findById(regularMember.getId()))
                .willReturn(Optional.of(regularMember));
        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), ownerCognitoSub))
                .willReturn(Optional.of(ownerMember));

        // when
        MessageResponse response = groupService.removeMember(ownerCognitoSub, group.getId(), regularMember.getId());

        // then
        assertThat(response.getMessage()).contains("제거");
        then(groupMemberRepository).should(times(1)).delete(regularMember);
    }

    @Test
    @DisplayName("멤버 제거 실패 - ADMIN은 MEMBER만 제거 가능")
    void test_removeMember_AdminCannotRemoveAdmin_ShouldThrowException() {
        // given
        String adminCognitoSub = "admin-cognito-sub-789";
        User admin = User.builder()
                .id(3L)
                .email("admin@example.com")
                .cognitoSub(adminCognitoSub)
                .build();

        GroupMember adminMember = GroupMember.builder()
                .id(3L)
                .groupId(group.getId())
                .userCognitoSub(adminCognitoSub)
                .role(GroupRole.ADMIN)
                .build();

        String anotherAdminCognitoSub = "another-admin-cognito-sub-000";
        GroupMember anotherAdmin = GroupMember.builder()
                .id(4L)
                .groupId(group.getId())
                .userCognitoSub(anotherAdminCognitoSub)
                .role(GroupRole.ADMIN)
                .build();

        given(groupMemberRepository.findById(anotherAdmin.getId()))
                .willReturn(Optional.of(anotherAdmin));
        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), adminCognitoSub))
                .willReturn(Optional.of(adminMember));

        // when & then
        assertThatThrownBy(() -> groupService.removeMember(adminCognitoSub, group.getId(), anotherAdmin.getId()))
                .isInstanceOf(InsufficientPermissionException.class)
                .hasMessageContaining("MEMBER");
    }

    @Test
    @DisplayName("그룹 탈퇴 성공 - MEMBER")
    void test_leaveGroup_AsMember_Success_ShouldLeaveGroup() {
        // given
        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), memberCognitoSub))
                .willReturn(Optional.of(regularMember));

        // when
        MessageResponse response = groupService.leaveGroup(memberCognitoSub, group.getId());

        // then
        assertThat(response.getMessage()).contains("탈퇴");
        then(groupMemberRepository).should(times(1)).delete(regularMember);
    }

    @Test
    @DisplayName("그룹 탈퇴 실패 - OWNER는 소유권 이전 필요")
    void test_leaveGroup_AsOwner_RequiresTransfer_ShouldThrowException() {
        // given
        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), ownerCognitoSub))
                .willReturn(Optional.of(ownerMember));
        given(groupMemberRepository.countByGroupId(group.getId()))
                .willReturn(2L); // 2명 이상

        // when & then
        assertThatThrownBy(() -> groupService.leaveGroup(ownerCognitoSub, group.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transfer ownership");
    }

    @Test
    @DisplayName("그룹 탈퇴 - OWNER가 마지막 멤버면 그룹 삭제 + Schedule-Service 호출")
    void test_leaveGroup_LastOwner_DeletesGroup_ShouldDeleteGroupAndCallScheduleService() {
        // given
        given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), ownerCognitoSub))
                .willReturn(Optional.of(ownerMember));
        given(groupMemberRepository.countByGroupId(group.getId()))
                .willReturn(1L); // 마지막 멤버
        given(scheduleServiceClient.deleteGroupData(group.getId()))
                .willReturn(true);

        // when
        MessageResponse response = groupService.leaveGroup(ownerCognitoSub, group.getId());

        // then
        assertThat(response.getMessage()).contains("탈퇴");
        then(scheduleServiceClient).should(times(1)).deleteGroupData(group.getId());
        then(groupMemberRepository).should(times(1)).deleteByGroupId(group.getId());
        then(groupRepository).should(times(1)).deleteById(group.getId());
    }

    @Nested
    @DisplayName("Internal API 테스트")
    class InternalApiTest {

        @Test
        @DisplayName("멤버십 조회 - 멤버인 경우")
        void test_getMembershipInfo_Member_ShouldReturnMemberInfo() {
            // given
            given(groupRepository.existsById(group.getId())).willReturn(true);
            given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), memberCognitoSub))
                    .willReturn(Optional.of(regularMember));

            // when
            GroupMembershipResponse response = groupService.getMembershipInfo(group.getId(), memberCognitoSub);

            // then
            assertThat(response.getGroupId()).isEqualTo(group.getId());
            assertThat(response.getCognitoSub()).isEqualTo(memberCognitoSub);
            assertThat(response.isMember()).isTrue();
            assertThat(response.getRole()).isEqualTo(GroupRole.MEMBER);
        }

        @Test
        @DisplayName("멤버십 조회 - 멤버가 아닌 경우")
        void test_getMembershipInfo_NotMember_ShouldReturnNotMemberInfo() {
            // given
            String nonMemberCognitoSub = "non-member-cognito-sub";
            given(groupRepository.existsById(group.getId())).willReturn(true);
            given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), nonMemberCognitoSub))
                    .willReturn(Optional.empty());

            // when
            GroupMembershipResponse response = groupService.getMembershipInfo(group.getId(), nonMemberCognitoSub);

            // then
            assertThat(response.getGroupId()).isEqualTo(group.getId());
            assertThat(response.getCognitoSub()).isEqualTo(nonMemberCognitoSub);
            assertThat(response.isMember()).isFalse();
            assertThat(response.getRole()).isNull();
        }

        @Test
        @DisplayName("멤버십 조회 - 그룹이 존재하지 않는 경우")
        void test_getMembershipInfo_GroupNotExists_ShouldReturnNotMemberInfo() {
            // given
            Long nonExistentGroupId = 999L;
            given(groupRepository.existsById(nonExistentGroupId)).willReturn(false);

            // when
            GroupMembershipResponse response = groupService.getMembershipInfo(nonExistentGroupId, memberCognitoSub);

            // then
            assertThat(response.getGroupId()).isEqualTo(nonExistentGroupId);
            assertThat(response.isMember()).isFalse();
            assertThat(response.getRole()).isNull();
        }

        @Test
        @DisplayName("멤버십 조회 - OWNER인 경우")
        void test_getMembershipInfo_Owner_ShouldReturnOwnerInfo() {
            // given
            given(groupRepository.existsById(group.getId())).willReturn(true);
            given(groupMemberRepository.findByGroupIdAndUserCognitoSub(group.getId(), ownerCognitoSub))
                    .willReturn(Optional.of(ownerMember));

            // when
            GroupMembershipResponse response = groupService.getMembershipInfo(group.getId(), ownerCognitoSub);

            // then
            assertThat(response.isMember()).isTrue();
            assertThat(response.getRole()).isEqualTo(GroupRole.OWNER);
        }
    }
}
