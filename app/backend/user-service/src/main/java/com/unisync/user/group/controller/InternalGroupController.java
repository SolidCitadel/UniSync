package com.unisync.user.group.controller;

import com.unisync.user.group.dto.GroupMembershipResponse;
import com.unisync.user.group.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 그룹 Internal API Controller
 *
 * 서비스 간 내부 통신용. API Gateway에서 외부 접근 차단됨.
 * Schedule-Service 등에서 그룹 멤버십 확인 시 사용.
 */
@RestController
@RequestMapping("/api/internal/groups")
@RequiredArgsConstructor
@Tag(name = "Internal - Groups", description = "그룹 Internal API (서비스 간 통신용)")
public class InternalGroupController {

    private final GroupService groupService;

    /**
     * 그룹 멤버십 조회
     *
     * Schedule-Service에서 그룹 일정/할일 접근 권한 체크 시 호출
     */
    @GetMapping("/{groupId}/members/{cognitoSub}")
    @Operation(summary = "그룹 멤버십 조회", description = "사용자가 그룹의 멤버인지, 어떤 역할인지 확인")
    public ResponseEntity<GroupMembershipResponse> checkMembership(
            @Parameter(description = "그룹 ID") @PathVariable Long groupId,
            @Parameter(description = "사용자 Cognito Sub") @PathVariable String cognitoSub
    ) {
        GroupMembershipResponse response = groupService.getMembershipInfo(groupId, cognitoSub);
        return ResponseEntity.ok(response);
    }

    /**
     * 그룹 멤버 cognitoSub 목록 조회
     *
     * Schedule-Service에서 공강 시간 찾기 시 사용
     */
    @GetMapping("/{groupId}/members/cognito-subs")
    @Operation(summary = "그룹 멤버 목록 조회", description = "그룹의 모든 멤버 cognitoSub 목록 반환")
    public ResponseEntity<List<String>> getGroupMemberCognitoSubs(
            @Parameter(description = "그룹 ID") @PathVariable Long groupId
    ) {
        List<String> cognitoSubs = groupService.getGroupMemberCognitoSubs(groupId);
        return ResponseEntity.ok(cognitoSubs);
    }

    /**
     * 사용자가 속한 모든 그룹 ID 목록 조회
     *
     * Schedule-Service에서 includeGroups=true인 경우 개인 + 그룹 일정을 함께 조회할 때 사용
     */
    @GetMapping("/memberships/{cognitoSub}")
    @Operation(summary = "사용자 그룹 목록 조회", description = "사용자가 속한 모든 그룹 ID를 반환합니다.")
    public ResponseEntity<List<Long>> getUserGroupIds(
            @Parameter(description = "사용자 Cognito Sub") @PathVariable String cognitoSub
    ) {
        List<Long> groupIds = groupService.getMyGroupIds(cognitoSub);
        return ResponseEntity.ok(groupIds);
    }
}
