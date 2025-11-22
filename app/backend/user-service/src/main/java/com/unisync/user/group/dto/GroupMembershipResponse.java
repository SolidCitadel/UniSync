package com.unisync.user.group.dto;

import com.unisync.user.common.entity.GroupRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 그룹 멤버십 응답 (Internal API용)
 *
 * Schedule-Service 등 다른 서비스에서 권한 체크 시 사용
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "그룹 멤버십 응답 (Internal API)")
public class GroupMembershipResponse {

    @Schema(description = "그룹 ID", example = "1")
    private Long groupId;

    @Schema(description = "사용자 Cognito Sub", example = "cognito-sub-uuid")
    private String cognitoSub;

    @Schema(description = "멤버 여부", example = "true")
    private boolean isMember;

    @Schema(description = "역할 (멤버가 아닌 경우 null)", example = "MEMBER")
    private GroupRole role;

    /**
     * 멤버가 아닌 경우의 응답 생성
     */
    public static GroupMembershipResponse notMember(Long groupId, String cognitoSub) {
        return GroupMembershipResponse.builder()
                .groupId(groupId)
                .cognitoSub(cognitoSub)
                .isMember(false)
                .role(null)
                .build();
    }

    /**
     * 멤버인 경우의 응답 생성
     */
    public static GroupMembershipResponse member(Long groupId, String cognitoSub, GroupRole role) {
        return GroupMembershipResponse.builder()
                .groupId(groupId)
                .cognitoSub(cognitoSub)
                .isMember(true)
                .role(role)
                .build();
    }
}
