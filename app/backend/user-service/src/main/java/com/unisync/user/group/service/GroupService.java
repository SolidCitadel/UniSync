package com.unisync.user.group.service;

import com.unisync.user.auth.exception.UserNotFoundException;
import com.unisync.user.common.client.ScheduleServiceClient;
import com.unisync.user.common.entity.*;
import com.unisync.user.common.repository.GroupMemberRepository;
import com.unisync.user.common.repository.GroupRepository;
import com.unisync.user.common.repository.UserRepository;
import com.unisync.user.friend.dto.MessageResponse;
import com.unisync.user.friend.dto.UserSummaryDto;
import com.unisync.user.group.dto.*;
import com.unisync.user.group.exception.GroupNotFoundException;
import com.unisync.user.group.exception.InsufficientPermissionException;
import com.unisync.user.group.exception.MemberAlreadyExistsException;
import com.unisync.user.group.exception.MemberNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ScheduleServiceClient scheduleServiceClient;

    /**
     * 그룹 생성
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param request    그룹 생성 요청
     * @return 그룹 응답
     */
    @Transactional
    public GroupResponse createGroup(String cognitoSub, GroupCreateRequest request) {
        User currentUser = getUserByCognitoSub(cognitoSub);

        // 그룹 생성
        Group group = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerCognitoSub(cognitoSub)
                .build();

        group = groupRepository.save(group);
        log.info("그룹 생성: groupId={}, ownerCognitoSub={}, name={}", group.getId(), cognitoSub, group.getName());

        // 소유자를 OWNER로 멤버 추가
        GroupMember ownerMember = GroupMember.builder()
                .groupId(group.getId())
                .userCognitoSub(cognitoSub)
                .role(GroupRole.OWNER)
                .build();
        groupMemberRepository.save(ownerMember);

        UserSummaryDto ownerInfo = UserSummaryDto.builder()
                .cognitoSub(currentUser.getCognitoSub())
                .name(currentUser.getName())
                .email(currentUser.getEmail())
                .build();

        return GroupResponse.from(group, ownerInfo, GroupRole.OWNER, 1L);
    }

    /**
     * 내가 속한 그룹 목록 조회
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @return 그룹 목록
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups(String cognitoSub) {
        List<GroupMember> memberships = groupMemberRepository.findByUserCognitoSub(cognitoSub);

        return memberships.stream()
                .map(membership -> {
                    Group group = groupRepository.findById(membership.getGroupId())
                            .orElseThrow(() -> new GroupNotFoundException(membership.getGroupId()));

                    User owner = userRepository.findByCognitoSub(group.getOwnerCognitoSub())
                            .orElseThrow(() -> new UserNotFoundException("Owner not found: " + group.getOwnerCognitoSub()));

                    UserSummaryDto ownerInfo = UserSummaryDto.builder()
                            .cognitoSub(owner.getCognitoSub())
                            .name(owner.getName())
                            .email(owner.getEmail())
                            .build();

                    long memberCount = groupMemberRepository.countByGroupId(group.getId());

                    return GroupResponse.from(group, ownerInfo, membership.getRole(), memberCount);
                })
                .collect(Collectors.toList());
    }

    /**
     * 사용자가 속한 모든 그룹 ID 조회 (Internal API용)
     */
    @Transactional(readOnly = true)
    public List<Long> getMyGroupIds(String cognitoSub) {
        return groupMemberRepository.findByUserCognitoSub(cognitoSub).stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toList());
    }

    /**
     * 그룹 상세 조회 (멤버 목록 포함)
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param groupId    그룹 ID
     * @return 그룹 상세 응답
     */
    @Transactional(readOnly = true)
    public GroupDetailResponse getGroupDetails(String cognitoSub, Long groupId) {
        // 그룹 멤버인지 확인
        checkMembership(cognitoSub, groupId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        User owner = userRepository.findByCognitoSub(group.getOwnerCognitoSub())
                .orElseThrow(() -> new UserNotFoundException("Owner not found: " + group.getOwnerCognitoSub()));

        UserSummaryDto ownerInfo = UserSummaryDto.builder()
                .cognitoSub(owner.getCognitoSub())
                .name(owner.getName())
                .email(owner.getEmail())
                .build();

        // 멤버 목록 조회
        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        List<MemberResponse> memberResponses = members.stream()
                .map(member -> {
                    User user = userRepository.findByCognitoSub(member.getUserCognitoSub())
                            .orElseThrow(() -> new UserNotFoundException("User not found: " + member.getUserCognitoSub()));

                    UserSummaryDto userInfo = UserSummaryDto.builder()
                            .cognitoSub(user.getCognitoSub())
                            .name(user.getName())
                            .email(user.getEmail())
                            .build();

                    return MemberResponse.from(member, userInfo);
                })
                .collect(Collectors.toList());

        return GroupDetailResponse.from(group, ownerInfo, memberResponses);
    }

    /**
     * 그룹 수정 (OWNER만)
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param groupId    그룹 ID
     * @param request    그룹 수정 요청
     * @return 그룹 응답
     */
    @Transactional
    public GroupResponse updateGroup(String cognitoSub, Long groupId, GroupUpdateRequest request) {
        User currentUser = getUserByCognitoSub(cognitoSub);

        // OWNER 권한 확인
        checkPermission(cognitoSub, groupId, GroupRole.OWNER);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        // 수정
        if (request.getName() != null) {
            group.setName(request.getName());
        }
        if (request.getDescription() != null) {
            group.setDescription(request.getDescription());
        }

        group = groupRepository.save(group);
        log.info("그룹 수정: groupId={}, name={}", group.getId(), group.getName());

        UserSummaryDto ownerInfo = UserSummaryDto.builder()
                .cognitoSub(currentUser.getCognitoSub())
                .name(currentUser.getName())
                .email(currentUser.getEmail())
                .build();

        long memberCount = groupMemberRepository.countByGroupId(groupId);

        return GroupResponse.from(group, ownerInfo, GroupRole.OWNER, memberCount);
    }

    /**
     * 그룹 삭제 (OWNER만)
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param groupId    그룹 ID
     * @return 메시지 응답
     */
    @Transactional
    public MessageResponse deleteGroup(String cognitoSub, Long groupId) {
        // OWNER 권한 확인
        checkPermission(cognitoSub, groupId, GroupRole.OWNER);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        // Schedule-Service의 그룹 데이터 삭제
        scheduleServiceClient.deleteGroupData(groupId);

        // 그룹의 모든 멤버 먼저 삭제
        groupMemberRepository.deleteByGroupId(groupId);

        // 그룹 삭제
        groupRepository.delete(group);
        log.info("그룹 삭제: groupId={}, name={}", groupId, group.getName());

        return MessageResponse.of("그룹을 삭제했습니다");
    }

    /**
     * 멤버 초대 (OWNER/ADMIN)
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param groupId    그룹 ID
     * @param request    멤버 초대 요청
     * @return 멤버 응답
     */
    @Transactional
    public MemberResponse inviteMember(String cognitoSub, Long groupId, MemberInviteRequest request) {
        // OWNER 또는 ADMIN 권한 확인
        checkPermission(cognitoSub, groupId, GroupRole.ADMIN);

        // 그룹 존재 확인
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        String invitedCognitoSub = request.getUserCognitoSub();

        // 초대할 사용자 존재 확인
        User invitedUser = userRepository.findByCognitoSub(invitedCognitoSub)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + invitedCognitoSub));

        // 이미 멤버인지 확인
        if (groupMemberRepository.existsByGroupIdAndUserCognitoSub(groupId, invitedCognitoSub)) {
            throw new MemberAlreadyExistsException(groupId, invitedCognitoSub);
        }

        // 멤버 추가
        GroupMember member = GroupMember.builder()
                .groupId(groupId)
                .userCognitoSub(invitedCognitoSub)
                .role(request.getRole())
                .build();

        member = groupMemberRepository.save(member);
        log.info("멤버 초대: groupId={}, userCognitoSub={}, role={}", groupId, invitedCognitoSub, request.getRole());

        UserSummaryDto userInfo = UserSummaryDto.builder()
                .cognitoSub(invitedUser.getCognitoSub())
                .name(invitedUser.getName())
                .email(invitedUser.getEmail())
                .build();

        return MemberResponse.from(member, userInfo);
    }

    /**
     * 멤버 목록 조회
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param groupId    그룹 ID
     * @return 멤버 목록
     */
    @Transactional(readOnly = true)
    public List<MemberResponse> getMembers(String cognitoSub, Long groupId) {
        // 그룹 멤버인지 확인
        checkMembership(cognitoSub, groupId);

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);

        return members.stream()
                .map(member -> {
                    User user = userRepository.findByCognitoSub(member.getUserCognitoSub())
                            .orElseThrow(() -> new UserNotFoundException("User not found: " + member.getUserCognitoSub()));

                    UserSummaryDto userInfo = UserSummaryDto.builder()
                            .cognitoSub(user.getCognitoSub())
                            .name(user.getName())
                            .email(user.getEmail())
                            .build();

                    return MemberResponse.from(member, userInfo);
                })
                .collect(Collectors.toList());
    }

    /**
     * 멤버 역할 변경 (OWNER만)
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param groupId    그룹 ID
     * @param memberId   멤버 ID
     * @param request    역할 변경 요청
     * @return 멤버 응답
     */
    @Transactional
    public MemberResponse updateMemberRole(String cognitoSub, Long groupId, Long memberId, UpdateRoleRequest request) {
        // OWNER 권한 확인
        checkPermission(cognitoSub, groupId, GroupRole.OWNER);

        // OWNER 역할로 변경은 불가 (소유권 이전은 별도 API)
        if (request.getRole() == GroupRole.OWNER) {
            throw new IllegalArgumentException("Cannot change role to OWNER. Use ownership transfer API instead");
        }

        GroupMember member = groupMemberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException("Member not found: " + memberId));

        // 그룹 ID 확인
        if (!member.getGroupId().equals(groupId)) {
            throw new IllegalArgumentException("Member does not belong to this group");
        }

        // 역할 변경
        member.setRole(request.getRole());
        GroupMember updatedMember = groupMemberRepository.save(member);
        log.info("멤버 역할 변경: memberId={}, newRole={}", memberId, request.getRole());

        User user = userRepository.findByCognitoSub(updatedMember.getUserCognitoSub())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + updatedMember.getUserCognitoSub()));

        UserSummaryDto userInfo = UserSummaryDto.builder()
                .cognitoSub(user.getCognitoSub())
                .name(user.getName())
                .email(user.getEmail())
                .build();

        return MemberResponse.from(updatedMember, userInfo);
    }

    /**
     * 멤버 제거 (OWNER/ADMIN)
     * - OWNER: 모든 멤버 제거 가능
     * - ADMIN: MEMBER만 제거 가능
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param groupId    그룹 ID
     * @param memberId   멤버 ID
     * @return 메시지 응답
     */
    @Transactional
    public MessageResponse removeMember(String cognitoSub, Long groupId, Long memberId) {
        // 멤버 조회
        GroupMember member = groupMemberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException("Member not found: " + memberId));

        // 그룹 ID 확인
        if (!member.getGroupId().equals(groupId)) {
            throw new IllegalArgumentException("Member does not belong to this group");
        }

        // 요청자의 권한 조회
        GroupMember requesterMember = groupMemberRepository.findByGroupIdAndUserCognitoSub(groupId, cognitoSub)
                .orElseThrow(() -> new MemberNotFoundException(groupId, cognitoSub));

        // 권한 확인
        if (requesterMember.getRole() == GroupRole.MEMBER) {
            throw new InsufficientPermissionException("Only OWNER or ADMIN can remove members");
        }

        // ADMIN은 MEMBER만 제거 가능
        if (requesterMember.getRole() == GroupRole.ADMIN && member.getRole() != GroupRole.MEMBER) {
            throw new InsufficientPermissionException("ADMIN can only remove MEMBER role");
        }

        // OWNER 자신은 제거 불가
        if (member.getRole() == GroupRole.OWNER) {
            throw new IllegalArgumentException("Cannot remove OWNER. Transfer ownership first or delete the group");
        }

        // 멤버 제거
        groupMemberRepository.delete(member);
        log.info("멤버 제거: groupId={}, userCognitoSub={}", groupId, member.getUserCognitoSub());

        return MessageResponse.of("멤버를 제거했습니다");
    }

    /**
     * 그룹 탈퇴 (본인)
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param groupId    그룹 ID
     * @return 메시지 응답
     */
    @Transactional
    public MessageResponse leaveGroup(String cognitoSub, Long groupId) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserCognitoSub(groupId, cognitoSub)
                .orElseThrow(() -> new MemberNotFoundException(groupId, cognitoSub));

        // OWNER는 탈퇴 전 소유권 이전 필요
        if (member.getRole() == GroupRole.OWNER) {
            long memberCount = groupMemberRepository.countByGroupId(groupId);
            if (memberCount > 1) {
                throw new IllegalArgumentException("Owner must transfer ownership before leaving the group");
            }
            // 마지막 멤버면 그룹 삭제 (Schedule 데이터 → 멤버 → 그룹 순서로 삭제)
            scheduleServiceClient.deleteGroupData(groupId);
            groupMemberRepository.deleteByGroupId(groupId);
            groupRepository.deleteById(groupId);
            log.info("마지막 멤버 탈퇴로 그룹 삭제: groupId={}", groupId);
        } else {
            // 멤버 탈퇴
            groupMemberRepository.delete(member);
            log.info("그룹 탈퇴: groupId={}, userCognitoSub={}", groupId, cognitoSub);
        }

        return MessageResponse.of("그룹에서 탈퇴했습니다");
    }

    // ========== Internal API ==========

    /**
     * 그룹 멤버십 조회 (Internal API)
     *
     * Schedule-Service 등 다른 서비스에서 권한 체크 시 사용
     *
     * @param groupId    그룹 ID
     * @param cognitoSub 사용자 Cognito Sub
     * @return 멤버십 정보 (멤버 여부, 역할)
     */
    @Transactional(readOnly = true)
    public GroupMembershipResponse getMembershipInfo(Long groupId, String cognitoSub) {
        // 그룹 존재 여부 확인 (존재하지 않으면 NotMember 반환)
        if (!groupRepository.existsById(groupId)) {
            log.debug("그룹 멤버십 조회 (Internal): 그룹 없음 - groupId={}", groupId);
            return GroupMembershipResponse.notMember(groupId, cognitoSub);
        }

        return groupMemberRepository.findByGroupIdAndUserCognitoSub(groupId, cognitoSub)
                .map(member -> {
                    log.debug("그룹 멤버십 조회 (Internal): 멤버 확인 - groupId={}, cognitoSub={}, role={}",
                            groupId, cognitoSub, member.getRole());
                    return GroupMembershipResponse.member(groupId, cognitoSub, member.getRole());
                })
                .orElseGet(() -> {
                    log.debug("그룹 멤버십 조회 (Internal): 멤버 아님 - groupId={}, cognitoSub={}", groupId, cognitoSub);
                    return GroupMembershipResponse.notMember(groupId, cognitoSub);
                });
    }

    /**
     * Internal API: 그룹의 모든 멤버 cognitoSub 목록 조회
     *
     * Schedule-Service에서 공강 시간 찾기 시 사용
     *
     * @param groupId 그룹 ID
     * @return cognitoSub 목록
     */
    @Transactional(readOnly = true)
    public List<String> getGroupMemberCognitoSubs(Long groupId) {
        log.debug("그룹 멤버 cognitoSub 목록 조회 (Internal) - groupId={}", groupId);

        // 그룹 존재 여부 확인
        if (!groupRepository.existsById(groupId)) {
            log.warn("그룹 멤버 cognitoSub 목록 조회 실패: 그룹 없음 - groupId={}", groupId);
            return List.of();
        }

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        List<String> cognitoSubs = members.stream()
                .map(GroupMember::getUserCognitoSub)
                .collect(Collectors.toList());

        log.debug("그룹 멤버 cognitoSub 목록 조회 완료 - groupId={}, memberCount={}", groupId, cognitoSubs.size());
        return cognitoSubs;
    }

    // ========== Helper methods ==========

    private User getUserByCognitoSub(String cognitoSub) {
        return userRepository.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new UserNotFoundException("User not found with cognitoSub: " + cognitoSub));
    }

    private void checkMembership(String cognitoSub, Long groupId) {
        if (!groupMemberRepository.existsByGroupIdAndUserCognitoSub(groupId, cognitoSub)) {
            throw new MemberNotFoundException(groupId, cognitoSub);
        }
    }

    private void checkPermission(String cognitoSub, Long groupId, GroupRole requiredRole) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserCognitoSub(groupId, cognitoSub)
                .orElseThrow(() -> new MemberNotFoundException(groupId, cognitoSub));

        if (!member.getRole().hasPermission(requiredRole)) {
            throw new InsufficientPermissionException(
                    String.format("Required role: %s, but user has: %s", requiredRole, member.getRole()));
        }
    }
}
