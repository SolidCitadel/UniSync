package com.unisync.user.user.controller;

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

@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
@Tag(name = "Internal - Users", description = "사용자 Internal API (서비스 간 통신용)")
public class InternalUserController {

    private final GroupService groupService;

    @GetMapping("/{cognitoSub}/groups")
    @Operation(summary = "사용자 그룹 목록 조회", description = "사용자가 속한 모든 그룹 ID를 반환합니다.")
    public ResponseEntity<List<Long>> getUserGroupIds(
            @Parameter(description = "사용자 Cognito Sub") @PathVariable String cognitoSub
    ) {
        List<Long> groupIds = groupService.getMyGroupIds(cognitoSub);
        return ResponseEntity.ok(groupIds);
    }
}
