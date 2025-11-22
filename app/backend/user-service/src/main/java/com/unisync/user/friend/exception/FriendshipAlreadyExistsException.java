package com.unisync.user.friend.exception;

/**
 * 이미 친구 관계가 존재할 때 발생
 */
public class FriendshipAlreadyExistsException extends RuntimeException {

    public FriendshipAlreadyExistsException(String message) {
        super(message);
    }

    public FriendshipAlreadyExistsException(String userCognitoSub, String friendCognitoSub) {
        super(String.format("Friendship already exists between user %s and user %s", userCognitoSub, friendCognitoSub));
    }
}
