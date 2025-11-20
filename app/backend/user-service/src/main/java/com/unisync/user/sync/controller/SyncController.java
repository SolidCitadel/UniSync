package com.unisync.user.sync.controller;

import com.unisync.user.common.util.JwtUtil;
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
@RequestMapping("/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final CanvasSyncService canvasSyncService;
    private final JwtUtil jwtUtil;

    /**
     * Canvas 수동 동기화 시작
     *
     * POST /v1/sync/canvas
     * Authorization: Bearer {JWT}
     *
     * @param authorization JWT Bearer 토큰
     * @return 동기화 시작 결과 (즉시 응답)
     */
    @PostMapping("/canvas")
    public ResponseEntity<CanvasSyncResponse> syncCanvas(
            @RequestHeader("Authorization") String authorization
    ) {
        log.info("POST /v1/sync/canvas - Canvas manual sync requested");

        // JWT에서 cognitoSub 추출
        String cognitoSub = jwtUtil.extractCognitoSub(authorization);

        log.debug("Extracted cognitoSub from JWT: {}", cognitoSub);

        // Canvas 동기화 시작 (Lambda 호출)
        CanvasSyncResponse response = canvasSyncService.syncCanvas(cognitoSub);

        log.info("Canvas sync response: {} courses, {} assignments",
                response.getCoursesCount(),
                response.getAssignmentsCount());

        return ResponseEntity.ok(response);
    }
}
