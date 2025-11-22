package com.unisync.user.friend.dto;

import com.unisync.user.common.entity.Friendship;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 받은 친구 요청 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "받은 친구 요청")
public class FriendRequestResponse {

    @Schema(description = "친구 요청 ID", example = "1")
    private Long requestId;

    @Schema(description = "요청 보낸 사용자 정보")
    private UserSummaryDto fromUser;

    @Schema(description = "요청 일시", example = "2025-11-22T10:00:00")
    private LocalDateTime createdAt;

    /**
     * Entity를 DTO로 변환
     *
     * @param friendship Friendship entity
     * @param fromUserInfo 요청자 정보
     * @return FriendRequestResponse
     */
    public static FriendRequestResponse from(Friendship friendship, UserSummaryDto fromUserInfo) {
        return FriendRequestResponse.builder()
                .requestId(friendship.getId())
                .fromUser(fromUserInfo)
                .createdAt(friendship.getCreatedAt())
                .build();
    }
}
