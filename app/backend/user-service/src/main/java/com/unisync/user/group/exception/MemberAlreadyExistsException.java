package com.unisync.user.group.exception;

/**
 * 이미 그룹 멤버일 때 발생
 */
public class MemberAlreadyExistsException extends RuntimeException {

    public MemberAlreadyExistsException(String message) {
        super(message);
    }

    public MemberAlreadyExistsException(Long groupId, String userCognitoSub) {
        super(String.format("User %s is already a member of group %d", userCognitoSub, groupId));
    }
}
