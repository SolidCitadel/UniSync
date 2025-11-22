package com.unisync.user.friend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 친구 요청 발송
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "친구 요청 발송")
public class FriendRequestRequest {

    @NotBlank(message = "친구 Cognito Sub는 필수입니다")
    @Schema(description = "친구로 추가할 사용자 Cognito Sub", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890", required = true)
    private String friendCognitoSub;
}
