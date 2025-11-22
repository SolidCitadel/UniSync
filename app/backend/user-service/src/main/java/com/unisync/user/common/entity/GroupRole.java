package com.unisync.user.common.entity;

import lombok.Getter;

/**
 * 그룹 내 역할
 */
@Getter
public enum GroupRole {
    /**
     * 일반 멤버 (읽기 전용)
     */
    MEMBER(1),

    /**
     * 관리자 (멤버 초대, MEMBER 제거, 일정/할일 관리)
     */
    ADMIN(2),

    /**
     * 소유자 (모든 권한)
     */
    OWNER(3);

    private final int priority;

    GroupRole(int priority) {
        this.priority = priority;
    }

    /**
     * 요구되는 역할 이상의 권한을 가지고 있는지 확인
     *
     * @param requiredRole 필요한 역할
     * @return true if this role has sufficient permissions
     */
    public boolean hasPermission(GroupRole requiredRole) {
        return this.priority >= requiredRole.priority;
    }
}
