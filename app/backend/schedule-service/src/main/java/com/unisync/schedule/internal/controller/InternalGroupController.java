package com.unisync.schedule.internal.controller;

import com.unisync.schedule.internal.dto.GroupDataDeleteResponse;
import com.unisync.schedule.internal.service.InternalGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그룹 Internal API Controller (Schedule-Service)
 *
 * 서비스 간 내부 통신용. API Gateway에서 외부 접근 차단됨.
 * User-Service에서 그룹 삭제 시 호출.
 */
@RestController
@RequestMapping("/api/internal/groups")
@RequiredArgsConstructor
@Tag(name = "Internal - Groups", description = "그룹 Internal API (서비스 간 통신용)")
public class InternalGroupController {

    private final InternalGroupService internalGroupService;

    /**
     * 그룹 데이터 삭제
     *
     * User-Service에서 그룹 삭제 시 호출. 그룹의 모든 일정/할일/카테고리 삭제.
     */
    @DeleteMapping("/{groupId}/data")
    @Operation(summary = "그룹 데이터 삭제", description = "그룹의 모든 일정, 할일, 카테고리 삭제")
    public ResponseEntity<GroupDataDeleteResponse> deleteGroupData(
            @Parameter(description = "그룹 ID") @PathVariable Long groupId
    ) {
        GroupDataDeleteResponse response = internalGroupService.deleteGroupData(groupId);
        return ResponseEntity.ok(response);
    }
}
