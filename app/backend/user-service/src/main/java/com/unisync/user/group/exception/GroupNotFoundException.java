package com.unisync.user.group.exception;

/**
 * 그룹을 찾을 수 없을 때 발생
 */
public class GroupNotFoundException extends RuntimeException {

    public GroupNotFoundException(String message) {
        super(message);
    }

    public GroupNotFoundException(Long groupId) {
        super("Group not found: " + groupId);
    }
}
