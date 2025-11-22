package com.unisync.user.group.dto;

import com.unisync.user.common.entity.Group;
import com.unisync.user.friend.dto.UserSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 그룹 상세 응답 (멤버 목록 포함)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "그룹 상세 응답")
public class GroupDetailResponse {

    @Schema(description = "그룹 ID", example = "1")
    private Long groupId;

    @Schema(description = "그룹명", example = "팀 프로젝트")
    private String name;

    @Schema(description = "그룹 설명", example = "소프트웨어 공학 팀 프로젝트")
    private String description;

    @Schema(description = "소유자 정보")
    private UserSummaryDto owner;

    @Schema(description = "멤버 목록")
    private List<MemberResponse> members;

    @Schema(description = "생성일시", example = "2025-11-22T10:00:00")
    private LocalDateTime createdAt;

    /**
     * Entity를 DTO로 변환 (상세 버전)
     */
    public static GroupDetailResponse from(Group group, UserSummaryDto owner, List<MemberResponse> members) {
        return GroupDetailResponse.builder()
                .groupId(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .owner(owner)
                .members(members)
                .createdAt(group.getCreatedAt())
                .build();
    }
}
