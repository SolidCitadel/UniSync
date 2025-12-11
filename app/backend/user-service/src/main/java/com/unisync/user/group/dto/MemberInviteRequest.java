package com.unisync.user.group.dto;

import com.unisync.user.common.entity.GroupRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 멤버 초대 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "멤버 초대 요청")
public class MemberInviteRequest {

    @NotBlank(message = "사용자 Cognito Sub는 필수입니다")
    @Schema(description = "초대할 사용자 Cognito Sub", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890", required = true)
    @JsonAlias("cognitoSub")
    private String userCognitoSub;

    @Schema(description = "역할", example = "MEMBER", defaultValue = "MEMBER")
    @Builder.Default
    private GroupRole role = GroupRole.MEMBER;
}
