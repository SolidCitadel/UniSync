package com.unisync.user.friend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사용자 요약 정보 (친구 검색 등에 사용)
 * cognitoSub 기반으로 사용자 식별
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 요약 정보")
public class UserSummaryDto {

    @Schema(description = "사용자 Cognito Sub (고유 식별자)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String cognitoSub;

    @Schema(description = "사용자 이름", example = "홍길동")
    private String name;

    @Schema(description = "이메일", example = "hong@example.com")
    private String email;

    @Schema(description = "이미 친구인지 여부", example = "false")
    private Boolean isFriend;

    @Schema(description = "친구 요청 대기 중인지 여부", example = "false")
    private Boolean isPending;
}
