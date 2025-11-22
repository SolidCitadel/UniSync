package com.unisync.schedule.internal.service;

import com.unisync.schedule.common.exception.UnauthorizedAccessException;
import com.unisync.schedule.internal.client.UserServiceClient;
import com.unisync.schedule.internal.dto.GroupMembershipResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 그룹 권한 검증 서비스
 *
 * User-Service Internal API를 호출하여 그룹 멤버십 및 권한 검증
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupPermissionService {

    private final UserServiceClient userServiceClient;

    /**
     * 그룹 읽기 권한 검증
     *
     * 그룹 멤버이면 읽기 가능
     *
     * @param groupId    그룹 ID (null이면 검증 스킵)
     * @param cognitoSub 사용자 Cognito Sub
     * @throws UnauthorizedAccessException 권한 없음
     */
    public void validateReadPermission(Long groupId, String cognitoSub) {
        if (groupId == null) {
            return; // 개인 데이터는 별도 검증
        }

        GroupMembershipResponse membership = userServiceClient.getMembership(groupId, cognitoSub);
        if (!membership.hasReadPermission()) {
            log.warn("그룹 읽기 권한 없음: groupId={}, cognitoSub={}", groupId, cognitoSub);
            throw new UnauthorizedAccessException("해당 그룹에 접근할 권한이 없습니다.");
        }

        log.debug("그룹 읽기 권한 확인: groupId={}, cognitoSub={}, role={}",
                groupId, cognitoSub, membership.getRole());
    }

    /**
     * 그룹 쓰기 권한 검증
     *
     * OWNER 또는 ADMIN만 생성/수정/삭제 가능
     *
     * @param groupId    그룹 ID (null이면 검증 스킵)
     * @param cognitoSub 사용자 Cognito Sub
     * @throws UnauthorizedAccessException 권한 없음
     */
    public void validateWritePermission(Long groupId, String cognitoSub) {
        if (groupId == null) {
            return; // 개인 데이터는 별도 검증
        }

        GroupMembershipResponse membership = userServiceClient.getMembership(groupId, cognitoSub);
        if (!membership.hasWritePermission()) {
            log.warn("그룹 쓰기 권한 없음: groupId={}, cognitoSub={}, role={}",
                    groupId, cognitoSub, membership.getRole());
            throw new UnauthorizedAccessException("해당 그룹의 일정을 수정할 권한이 없습니다. OWNER 또는 ADMIN만 가능합니다.");
        }

        log.debug("그룹 쓰기 권한 확인: groupId={}, cognitoSub={}, role={}",
                groupId, cognitoSub, membership.getRole());
    }

    /**
     * 그룹 멤버십 여부 확인 (예외 없이)
     *
     * @param groupId    그룹 ID
     * @param cognitoSub 사용자 Cognito Sub
     * @return 멤버십 정보
     */
    public GroupMembershipResponse getMembership(Long groupId, String cognitoSub) {
        return userServiceClient.getMembership(groupId, cognitoSub);
    }
}
