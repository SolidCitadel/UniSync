package com.unisync.user.friend.dto;

import com.unisync.user.common.entity.Friendship;
import com.unisync.user.common.entity.FriendshipStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 친구 관계 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "친구 관계 응답")
public class FriendshipResponse {

    @Schema(description = "친구 관계 ID", example = "1")
    private Long friendshipId;

    @Schema(description = "친구 정보")
    private UserSummaryDto friend;

    @Schema(description = "친구 관계 상태", example = "ACCEPTED")
    private FriendshipStatus status;

    @Schema(description = "생성일시", example = "2025-11-22T10:00:00")
    private LocalDateTime createdAt;

    /**
     * Entity를 DTO로 변환
     *
     * @param friendship Friendship entity
     * @param friendInfo 친구 정보
     * @return FriendshipResponse
     */
    public static FriendshipResponse from(Friendship friendship, UserSummaryDto friendInfo) {
        return FriendshipResponse.builder()
                .friendshipId(friendship.getId())
                .friend(friendInfo)
                .status(friendship.getStatus())
                .createdAt(friendship.getCreatedAt())
                .build();
    }
}
