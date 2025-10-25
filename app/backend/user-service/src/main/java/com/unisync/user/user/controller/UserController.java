package com.unisync.user.user.controller;

import com.unisync.user.user.dto.UserResponse;
import com.unisync.user.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * UserController - API Gateway를 거쳐서 들어오는 요청 처리
 *
 * API Gateway가 JWT를 검증하고 다음 헤더를 추가합니다:
 * - X-Cognito-Sub: Cognito User Pool의 sub (UUID) - 사용자 식별용
 * - X-User-Email: 사용자 이메일 (참고용)
 * - X-User-Name: 사용자 이름 (참고용)
 *
 * 이 컨트롤러는 이미 인증된 사용자의 헤더를 받아서 처리하므로, JWT 검증을 다시 하지 않습니다.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "사용자 API", description = "API Gateway를 거쳐서 들어오는 사용자 관련 API")
public class UserController {

    private final UserService userService;

    /**
     * 내 정보 조회 - API Gateway 헤더 사용
     *
     * API Gateway가 JWT를 검증하고 X-Cognito-Sub 헤더를 추가한 상태입니다.
     * 따라서 이 컨트롤러는 헤더만 읽어서 사용하면 됩니다.
     */
    @GetMapping("/me")
    @Operation(
            summary = "내 정보 조회 (API Gateway 헤더 사용)",
            description = "API Gateway가 JWT를 검증하고 전달한 X-Cognito-Sub 헤더로 사용자 정보 조회"
    )
    public ResponseEntity<UserResponse> getMyInfo(
            @Parameter(description = "API Gateway가 추가한 Cognito Sub (UUID)", required = true)
            @RequestHeader("X-Cognito-Sub") String cognitoSub,

            @Parameter(description = "API Gateway가 추가한 사용자 이메일")
            @RequestHeader(value = "X-User-Email", required = false) String email,

            @Parameter(description = "API Gateway가 추가한 사용자 이름")
            @RequestHeader(value = "X-User-Name", required = false) String name
    ) {
        log.info("사용자 정보 조회 요청: cognitoSub={}, email={}, name={}", cognitoSub, email, name);

        // API Gateway가 이미 JWT를 검증했으므로, cognitoSub를 신뢰하고 사용
        UserResponse user = userService.getUserByCognitoSub(cognitoSub);

        return ResponseEntity.ok(user);
    }
}