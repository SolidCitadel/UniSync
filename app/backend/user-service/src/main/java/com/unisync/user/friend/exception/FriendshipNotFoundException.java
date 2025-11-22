package com.unisync.user.friend.exception;

/**
 * 친구 관계를 찾을 수 없을 때 발생
 */
public class FriendshipNotFoundException extends RuntimeException {

    public FriendshipNotFoundException(String message) {
        super(message);
    }

    public FriendshipNotFoundException(Long friendshipId) {
        super("Friendship not found: " + friendshipId);
    }
}
