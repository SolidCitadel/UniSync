package com.unisync.user.friend.controller;

import com.unisync.user.friend.dto.*;
import com.unisync.user.friend.service.FriendService;
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
@RequestMapping("/v1/friends")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "친구 관리 API", description = "친구 추가, 요청 수락/거절, 친구 목록 조회 등")
public class FriendController {

    private final FriendService friendService;

    @GetMapping("/search")
    @Operation(summary = "사용자 검색", description = "이메일 또는 이름으로 사용자 검색")
    public ResponseEntity<List<UserSummaryDto>> searchUsers(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("사용자 검색: query={}, limit={}", query, limit);
        List<UserSummaryDto> users = friendService.searchUsers(cognitoSub, query, limit);
        return ResponseEntity.ok(users);
    }

    @PostMapping("/requests")
    @Operation(summary = "친구 요청 발송", description = "특정 사용자에게 친구 요청을 보냅니다")
    public ResponseEntity<FriendshipResponse> sendFriendRequest(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody FriendRequestRequest request
    ) {
        log.info("친구 요청 발송: friendCognitoSub={}", request.getFriendCognitoSub());
        FriendshipResponse response = friendService.sendFriendRequest(cognitoSub, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/requests/pending")
    @Operation(summary = "받은 친구 요청 목록", description = "본인이 받은 친구 요청 목록 조회")
    public ResponseEntity<List<FriendRequestResponse>> getPendingRequests(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub
    ) {
        log.info("받은 친구 요청 목록 조회");
        List<FriendRequestResponse> requests = friendService.getPendingRequests(cognitoSub);
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/requests/{requestId}/accept")
    @Operation(summary = "친구 요청 수락", description = "친구 요청을 수락하여 양방향 친구 관계를 형성합니다")
    public ResponseEntity<MessageResponse> acceptFriendRequest(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable Long requestId
    ) {
        log.info("친구 요청 수락: requestId={}", requestId);
        MessageResponse response = friendService.acceptFriendRequest(cognitoSub, requestId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/requests/{requestId}/reject")
    @Operation(summary = "친구 요청 거절", description = "친구 요청을 거절합니다")
    public ResponseEntity<MessageResponse> rejectFriendRequest(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable Long requestId
    ) {
        log.info("친구 요청 거절: requestId={}", requestId);
        MessageResponse response = friendService.rejectFriendRequest(cognitoSub, requestId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "친구 목록 조회", description = "본인의 친구 목록 조회 (ACCEPTED 상태)")
    public ResponseEntity<List<FriendshipResponse>> getFriends(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub
    ) {
        log.info("친구 목록 조회");
        List<FriendshipResponse> friends = friendService.getFriends(cognitoSub);
        return ResponseEntity.ok(friends);
    }

    @DeleteMapping("/{friendshipId}")
    @Operation(summary = "친구 삭제", description = "친구 관계를 삭제합니다 (양방향 삭제)")
    public ResponseEntity<MessageResponse> deleteFriend(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable Long friendshipId
    ) {
        log.info("친구 삭제: friendshipId={}", friendshipId);
        MessageResponse response = friendService.deleteFriend(cognitoSub, friendshipId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{friendCognitoSub}/block")
    @Operation(summary = "사용자 차단", description = "특정 사용자를 차단합니다")
    public ResponseEntity<MessageResponse> blockUser(
            @Parameter(hidden = true) @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @PathVariable String friendCognitoSub
    ) {
        log.info("사용자 차단: friendCognitoSub={}", friendCognitoSub);
        MessageResponse response = friendService.blockUser(cognitoSub, friendCognitoSub);
        return ResponseEntity.ok(response);
    }
}
