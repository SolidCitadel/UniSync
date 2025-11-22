package com.unisync.user.group.dto;

import com.unisync.user.common.entity.Group;
import com.unisync.user.common.entity.GroupRole;
import com.unisync.user.friend.dto.UserSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 그룹 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "그룹 응답")
public class GroupResponse {

    @Schema(description = "그룹 ID", example = "1")
    private Long groupId;

    @Schema(description = "그룹명", example = "팀 프로젝트")
    private String name;

    @Schema(description = "그룹 설명", example = "소프트웨어 공학 팀 프로젝트")
    private String description;

    @Schema(description = "소유자 정보")
    private UserSummaryDto owner;

    @Schema(description = "본인의 역할 (목록 조회 시)", example = "OWNER")
    private GroupRole myRole;

    @Schema(description = "멤버 수 (목록 조회 시)", example = "5")
    private Long memberCount;

    @Schema(description = "생성일시", example = "2025-11-22T10:00:00")
    private LocalDateTime createdAt;

    /**
     * Entity를 DTO로 변환 (간단한 버전, 목록 조회용)
     */
    public static GroupResponse from(Group group, UserSummaryDto owner, GroupRole myRole, Long memberCount) {
        return GroupResponse.builder()
                .groupId(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .owner(owner)
                .myRole(myRole)
                .memberCount(memberCount)
                .createdAt(group.getCreatedAt())
                .build();
    }
}
