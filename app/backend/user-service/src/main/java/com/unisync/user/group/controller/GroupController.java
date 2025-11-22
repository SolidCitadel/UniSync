package com.unisync.user.group.controller;

import com.unisync.user.friend.dto.MessageResponse;
import com.unisync.user.group.dto.*;
import com.unisync.user.group.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/groups")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "그룹 관리 API", description = "그룹 생성, 수정, 삭제, 멤버 관리 등")
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @Operation(summary = "그룹 생성", description = "새 그룹을 생성합니다. 생성자는 자동으로 OWNER가 됩니다")
    public ResponseEntity<GroupResponse> createGroup(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody GroupCreateRequest request
    ) {
        log.info("그룹 생성 요청: name={}", request.getName());
        GroupResponse response = groupService.createGroup(cognitoSub, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "내가 속한 그룹 목록 조회", description = "본인이 멤버로 속한 모든 그룹 목록 조회")
    public ResponseEntity<List<GroupResponse>> getMyGroups(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub
    ) {
        log.info("내 그룹 목록 조회");
        List<GroupResponse> groups = groupService.getMyGroups(cognitoSub);
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "그룹 상세 조회", description = "그룹의 상세 정보 및 멤버 목록 조회")
    public ResponseEntity<GroupDetailResponse> getGroupDetails(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable Long groupId
    ) {
        log.info("그룹 상세 조회: groupId={}", groupId);
        GroupDetailResponse response = groupService.getGroupDetails(cognitoSub, groupId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "그룹 수정", description = "그룹 정보를 수정합니다 (OWNER만 가능)")
    public ResponseEntity<GroupResponse> updateGroup(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable Long groupId,
            @Valid @RequestBody GroupUpdateRequest request
    ) {
        log.info("그룹 수정 요청: groupId={}", groupId);
        GroupResponse response = groupService.updateGroup(cognitoSub, groupId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "그룹 삭제", description = "그룹을 삭제합니다 (OWNER만 가능)")
    public ResponseEntity<MessageResponse> deleteGroup(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable Long groupId
    ) {
        log.info("그룹 삭제 요청: groupId={}", groupId);
        MessageResponse response = groupService.deleteGroup(cognitoSub, groupId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{groupId}/members")
    @Operation(summary = "멤버 초대", description = "그룹에 새 멤버를 초대합니다 (OWNER/ADMIN만 가능)")
    public ResponseEntity<MemberResponse> inviteMember(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable Long groupId,
            @Valid @RequestBody MemberInviteRequest request
    ) {
        log.info("멤버 초대: groupId={}, userCognitoSub={}", groupId, request.getUserCognitoSub());
        MemberResponse response = groupService.inviteMember(cognitoSub, groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{groupId}/members")
    @Operation(summary = "멤버 목록 조회", description = "그룹의 모든 멤버 목록 조회")
    public ResponseEntity<List<MemberResponse>> getMembers(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable Long groupId
    ) {
        log.info("멤버 목록 조회: groupId={}", groupId);
        List<MemberResponse> members = groupService.getMembers(cognitoSub, groupId);
        return ResponseEntity.ok(members);
    }

    @PatchMapping("/{groupId}/members/{memberId}/role")
    @Operation(summary = "멤버 역할 변경", description = "멤버의 역할을 변경합니다 (OWNER만 가능)")
    public ResponseEntity<MemberResponse> updateMemberRole(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable Long groupId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        log.info("멤버 역할 변경: groupId={}, memberId={}, newRole={}", groupId, memberId, request.getRole());
        MemberResponse response = groupService.updateMemberRole(cognitoSub, groupId, memberId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    @Operation(summary = "멤버 제거", description = "그룹에서 멤버를 제거합니다 (OWNER: 모든 멤버, ADMIN: MEMBER만 가능)")
    public ResponseEntity<MessageResponse> removeMember(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable Long groupId,
            @PathVariable Long memberId
    ) {
        log.info("멤버 제거: groupId={}, memberId={}", groupId, memberId);
        MessageResponse response = groupService.removeMember(cognitoSub, groupId, memberId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{groupId}/leave")
    @Operation(summary = "그룹 탈퇴", description = "본인이 그룹에서 탈퇴합니다 (OWNER는 소유권 이전 후 가능)")
    public ResponseEntity<MessageResponse> leaveGroup(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable Long groupId
    ) {
        log.info("그룹 탈퇴: groupId={}", groupId);
        MessageResponse response = groupService.leaveGroup(cognitoSub, groupId);
        return ResponseEntity.ok(response);
    }
}
