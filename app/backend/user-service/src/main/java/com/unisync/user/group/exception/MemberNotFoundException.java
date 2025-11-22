package com.unisync.user.group.exception;

/**
 * 그룹 멤버를 찾을 수 없을 때 발생
 */
public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(String message) {
        super(message);
    }

    public MemberNotFoundException(Long groupId, String userCognitoSub) {
        super(String.format("User %s is not a member of group %d", userCognitoSub, groupId));
    }
}
