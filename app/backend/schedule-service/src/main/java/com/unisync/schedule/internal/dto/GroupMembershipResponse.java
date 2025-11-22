package com.unisync.schedule.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 그룹 멤버십 응답 (User-Service Internal API 호출 결과)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMembershipResponse {

    private Long groupId;
    private String cognitoSub;
    private boolean isMember;
    private String role;  // OWNER, ADMIN, MEMBER

    /**
     * OWNER 또는 ADMIN 권한인지 확인
     */
    public boolean hasWritePermission() {
        return isMember && ("OWNER".equals(role) || "ADMIN".equals(role));
    }

    /**
     * 읽기 권한이 있는지 확인 (멤버이면 모두 가능)
     */
    public boolean hasReadPermission() {
        return isMember;
    }
}
