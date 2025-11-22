package com.unisync.user.group.dto;

import com.unisync.user.common.entity.GroupMember;
import com.unisync.user.common.entity.GroupRole;
import com.unisync.user.friend.dto.UserSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 그룹 멤버 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "그룹 멤버 응답")
public class MemberResponse {

    @Schema(description = "멤버십 ID", example = "1")
    private Long memberId;

    @Schema(description = "사용자 정보")
    private UserSummaryDto user;

    @Schema(description = "역할", example = "MEMBER")
    private GroupRole role;

    @Schema(description = "가입일시", example = "2025-11-22T10:00:00")
    private LocalDateTime joinedAt;

    /**
     * Entity를 DTO로 변환
     */
    public static MemberResponse from(GroupMember member, UserSummaryDto user) {
        return MemberResponse.builder()
                .memberId(member.getId())
                .user(user)
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
