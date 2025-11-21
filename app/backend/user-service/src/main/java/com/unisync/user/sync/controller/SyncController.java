package com.unisync.user.sync.controller;

import com.unisync.user.sync.dto.CanvasSyncResponse;
import com.unisync.user.sync.service.CanvasSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Canvas 수동 동기화 API
 * Phase 1: 사용자가 "동기화" 버튼 클릭 시 호출
 */
@Slf4j
@RestController
@RequestMapping("/v1/integrations/canvas")
@RequiredArgsConstructor
public class SyncController {

    private final CanvasSyncService canvasSyncService;

    /**
     * Canvas 수동 동기화 시작
     *
     * POST /v1/integrations/canvas/sync
     * X-Cognito-Sub: API Gateway에서 JWT 검증 후 전달
     *
     * @param cognitoSub Cognito 사용자 ID (X-Cognito-Sub 헤더)
     * @return 동기화 시작 결과 (즉시 응답)
     */
    @PostMapping("/sync")
    public ResponseEntity<CanvasSyncResponse> syncCanvas(
            @RequestHeader("X-Cognito-Sub") String cognitoSub
    ) {
        log.info("POST /v1/integrations/canvas/sync - Canvas manual sync requested");
        log.debug("Cognito sub from header: {}", cognitoSub);

        // Canvas 동기화 시작 (Lambda 호출)
        CanvasSyncResponse response = canvasSyncService.syncCanvas(cognitoSub);

        log.info("Canvas sync response: {} courses, {} assignments",
                response.getCoursesCount(),
                response.getAssignmentsCount());

        return ResponseEntity.ok(response);
    }
}
