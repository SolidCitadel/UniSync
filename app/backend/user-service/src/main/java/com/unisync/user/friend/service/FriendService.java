package com.unisync.user.friend.service;

import com.unisync.user.auth.exception.UserNotFoundException;
import com.unisync.user.common.entity.Friendship;
import com.unisync.user.common.entity.FriendshipStatus;
import com.unisync.user.common.entity.User;
import com.unisync.user.common.repository.FriendshipRepository;
import com.unisync.user.common.repository.UserRepository;
import com.unisync.user.friend.dto.*;
import com.unisync.user.friend.exception.FriendshipAlreadyExistsException;
import com.unisync.user.friend.exception.FriendshipNotFoundException;
import com.unisync.user.friend.exception.SelfFriendshipException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    /**
     * 사용자 검색 (이메일 또는 이름)
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param query      검색어 (이메일 또는 이름)
     * @param limit      결과 개수 제한
     * @return 검색 결과 목록
     */
    @Transactional(readOnly = true)
    public List<UserSummaryDto> searchUsers(String cognitoSub, String query, int limit) {
        // 차단한 사용자 목록 조회
        List<String> blockedCognitoSubs = friendshipRepository.findBlockedCognitoSubs(cognitoSub);

        // 사용자 검색 (이메일 또는 이름으로)
        List<User> users = userRepository.findAll().stream()
                .filter(user -> !user.getCognitoSub().equals(cognitoSub)) // 본인 제외
                .filter(user -> !blockedCognitoSubs.contains(user.getCognitoSub())) // 차단한 사용자 제외
                .filter(user -> user.getEmail().contains(query) || user.getName().contains(query))
                .limit(limit)
                .collect(Collectors.toList());

        // DTO 변환
        return users.stream()
                .map(user -> {
                    Friendship friendship = friendshipRepository
                            .findByUserCognitoSubAndFriendCognitoSub(cognitoSub, user.getCognitoSub())
                            .orElse(null);

                    return UserSummaryDto.builder()
                            .cognitoSub(user.getCognitoSub())
                            .name(user.getName())
                            .email(user.getEmail())
                            .isFriend(friendship != null && friendship.getStatus() == FriendshipStatus.ACCEPTED)
                            .isPending(friendship != null && friendship.getStatus() == FriendshipStatus.PENDING)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 친구 요청 발송
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param request    친구 요청
     * @return 친구 관계 응답
     */
    @Transactional
    public FriendshipResponse sendFriendRequest(String cognitoSub, FriendRequestRequest request) {
        String friendCognitoSub = request.getFriendCognitoSub();

        // 자기 자신에게 요청 불가
        if (cognitoSub.equals(friendCognitoSub)) {
            throw new SelfFriendshipException();
        }

        // 친구 존재 여부 확인
        User friend = userRepository.findByCognitoSub(friendCognitoSub)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + friendCognitoSub));

        // 이미 친구 관계가 있는지 확인 (양방향)
        if (friendshipRepository.existsFriendshipBetween(cognitoSub, friendCognitoSub)) {
            throw new FriendshipAlreadyExistsException(cognitoSub, friendCognitoSub);
        }

        // 친구 요청 생성
        Friendship friendship = Friendship.builder()
                .userCognitoSub(cognitoSub)
                .friendCognitoSub(friendCognitoSub)
                .status(FriendshipStatus.PENDING)
                .build();

        friendship = friendshipRepository.save(friendship);
        log.info("친구 요청 발송: userCognitoSub={}, friendCognitoSub={}", cognitoSub, friendCognitoSub);

        UserSummaryDto friendInfo = UserSummaryDto.builder()
                .cognitoSub(friend.getCognitoSub())
                .name(friend.getName())
                .email(friend.getEmail())
                .build();

        return FriendshipResponse.from(friendship, friendInfo);
    }

    /**
     * 받은 친구 요청 목록 조회
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @return 친구 요청 목록
     */
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> getPendingRequests(String cognitoSub) {
        List<Friendship> friendships = friendshipRepository.findByFriendCognitoSubAndStatus(
                cognitoSub, FriendshipStatus.PENDING);

        return friendships.stream()
                .map(friendship -> {
                    User fromUser = userRepository.findByCognitoSub(friendship.getUserCognitoSub())
                            .orElseThrow(() -> new UserNotFoundException("User not found: " + friendship.getUserCognitoSub()));

                    UserSummaryDto fromUserInfo = UserSummaryDto.builder()
                            .cognitoSub(fromUser.getCognitoSub())
                            .name(fromUser.getName())
                            .email(fromUser.getEmail())
                            .build();

                    return FriendRequestResponse.from(friendship, fromUserInfo);
                })
                .collect(Collectors.toList());
    }

    /**
     * 친구 요청 수락
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param requestId  친구 요청 ID
     * @return 메시지 응답
     */
    @Transactional
    public MessageResponse acceptFriendRequest(String cognitoSub, Long requestId) {
        // 친구 요청 조회
        Friendship friendship = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new FriendshipNotFoundException(requestId));

        // 요청 수신자인지 확인
        if (!friendship.getFriendCognitoSub().equals(cognitoSub)) {
            throw new IllegalArgumentException("You are not the recipient of this friend request");
        }

        // 이미 수락되었는지 확인
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new FriendshipAlreadyExistsException("Friend request already accepted");
        }

        // 1. 요청 수락 (status 업데이트)
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);

        // 2. 역방향 친구 관계 생성 (양방향)
        Friendship reverseFriendship = Friendship.builder()
                .userCognitoSub(cognitoSub)
                .friendCognitoSub(friendship.getUserCognitoSub())
                .status(FriendshipStatus.ACCEPTED)
                .build();
        friendshipRepository.save(reverseFriendship);

        log.info("친구 요청 수락: requestId={}, userCognitoSub={}, friendCognitoSub={}",
                requestId, friendship.getUserCognitoSub(), cognitoSub);

        return MessageResponse.of("친구 요청을 수락했습니다");
    }

    /**
     * 친구 요청 거절
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @param requestId  친구 요청 ID
     * @return 메시지 응답
     */
    @Transactional
    public MessageResponse rejectFriendRequest(String cognitoSub, Long requestId) {
        // 친구 요청 조회
        Friendship friendship = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new FriendshipNotFoundException(requestId));

        // 요청 수신자인지 확인
        if (!friendship.getFriendCognitoSub().equals(cognitoSub)) {
            throw new IllegalArgumentException("You are not the recipient of this friend request");
        }

        // 친구 요청 삭제
        friendshipRepository.delete(friendship);
        log.info("친구 요청 거절: requestId={}", requestId);

        return MessageResponse.of("친구 요청을 거절했습니다");
    }

    /**
     * 친구 목록 조회
     *
     * @param cognitoSub 요청자 Cognito Sub
     * @return 친구 목록
     */
    @Transactional(readOnly = true)
    public List<FriendshipResponse> getFriends(String cognitoSub) {
        List<Friendship> friendships = friendshipRepository.findByUserCognitoSubAndStatus(
                cognitoSub, FriendshipStatus.ACCEPTED);

        return friendships.stream()
                .map(friendship -> {
                    User friend = userRepository.findByCognitoSub(friendship.getFriendCognitoSub())
                            .orElseThrow(() -> new UserNotFoundException("User not found: " + friendship.getFriendCognitoSub()));

                    UserSummaryDto friendInfo = UserSummaryDto.builder()
                            .cognitoSub(friend.getCognitoSub())
                            .name(friend.getName())
                            .email(friend.getEmail())
                            .build();

                    return FriendshipResponse.from(friendship, friendInfo);
                })
                .collect(Collectors.toList());
    }

    /**
     * 친구 삭제 (양방향 삭제)
     *
     * @param cognitoSub   요청자 Cognito Sub
     * @param friendshipId 친구 관계 ID
     * @return 메시지 응답
     */
    @Transactional
    public MessageResponse deleteFriend(String cognitoSub, Long friendshipId) {
        // 친구 관계 조회
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new FriendshipNotFoundException(friendshipId));

        // 본인의 친구 관계인지 확인
        if (!friendship.getUserCognitoSub().equals(cognitoSub)) {
            throw new IllegalArgumentException("You are not the owner of this friendship");
        }

        String friendCognitoSub = friendship.getFriendCognitoSub();

        // 1. 순방향 삭제
        friendshipRepository.delete(friendship);

        // 2. 역방향 삭제
        friendshipRepository.deleteByUserCognitoSubAndFriendCognitoSub(friendCognitoSub, cognitoSub);

        log.info("친구 삭제: userCognitoSub={}, friendCognitoSub={}", cognitoSub, friendCognitoSub);

        return MessageResponse.of("친구를 삭제했습니다");
    }

    /**
     * 친구 차단
     *
     * @param cognitoSub       요청자 Cognito Sub
     * @param friendCognitoSub 차단할 사용자 Cognito Sub
     * @return 메시지 응답
     */
    @Transactional
    public MessageResponse blockUser(String cognitoSub, String friendCognitoSub) {
        // 자기 자신 차단 불가
        if (cognitoSub.equals(friendCognitoSub)) {
            throw new SelfFriendshipException("Cannot block yourself");
        }

        // 친구 존재 여부 확인
        userRepository.findByCognitoSub(friendCognitoSub)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + friendCognitoSub));

        // 기존 친구 관계 확인
        Friendship friendship = friendshipRepository.findByUserCognitoSubAndFriendCognitoSub(cognitoSub, friendCognitoSub)
                .orElse(null);

        if (friendship != null) {
            // 기존 관계가 있으면 상태 변경
            friendship.setStatus(FriendshipStatus.BLOCKED);
            friendshipRepository.save(friendship);
        } else {
            // 관계가 없으면 새로 생성
            friendship = Friendship.builder()
                    .userCognitoSub(cognitoSub)
                    .friendCognitoSub(friendCognitoSub)
                    .status(FriendshipStatus.BLOCKED)
                    .build();
            friendshipRepository.save(friendship);
        }

        // 역방향 관계 삭제 (차단되면 상대방도 나를 볼 수 없음)
        friendshipRepository.deleteByUserCognitoSubAndFriendCognitoSub(friendCognitoSub, cognitoSub);

        log.info("사용자 차단: userCognitoSub={}, blockedCognitoSub={}", cognitoSub, friendCognitoSub);

        return MessageResponse.of("사용자를 차단했습니다");
    }
}
