package com.unisync.user.common.entity;

/**
 * 친구 관계 상태
 */
public enum FriendshipStatus {
    /**
     * 친구 요청 대기 중
     */
    PENDING,

    /**
     * 친구 관계 성립
     */
    ACCEPTED,

    /**
     * 차단
     */
    BLOCKED
}
