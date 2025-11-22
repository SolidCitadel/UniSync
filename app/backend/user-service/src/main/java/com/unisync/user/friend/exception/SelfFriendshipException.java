package com.unisync.user.friend.exception;

/**
 * 자기 자신에게 친구 요청할 때 발생
 */
public class SelfFriendshipException extends RuntimeException {

    public SelfFriendshipException() {
        super("Cannot create friendship with yourself");
    }

    public SelfFriendshipException(String message) {
        super(message);
    }
}
