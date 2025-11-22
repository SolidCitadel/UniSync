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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FriendService 단위 테스트")
class FriendServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FriendService friendService;

    private User currentUser;
    private User friendUser;
    private Friendship friendship;
    private String cognitoSub;
    private String friendCognitoSub;

    @BeforeEach
    void setUp() {
        cognitoSub = "test-cognito-sub-123";
        friendCognitoSub = "friend-cognito-sub-456";

        currentUser = User.builder()
                .id(1L)
                .email("user1@example.com")
                .name("사용자1")
                .cognitoSub(cognitoSub)
                .isActive(true)
                .build();

        friendUser = User.builder()
                .id(2L)
                .email("user2@example.com")
                .name("사용자2")
                .cognitoSub(friendCognitoSub)
                .isActive(true)
                .build();

        friendship = Friendship.builder()
                .id(1L)
                .userCognitoSub(cognitoSub)
                .friendCognitoSub(friendCognitoSub)
                .status(FriendshipStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("친구 요청 발송 성공")
    void test_sendFriendRequest_Success_ShouldCreateFriendship() {
        // given
        FriendRequestRequest request = FriendRequestRequest.builder()
                .friendCognitoSub(friendCognitoSub)
                .build();

        given(userRepository.findByCognitoSub(friendCognitoSub))
                .willReturn(Optional.of(friendUser));
        given(friendshipRepository.existsFriendshipBetween(cognitoSub, friendCognitoSub))
                .willReturn(false);
        given(friendshipRepository.save(any(Friendship.class)))
                .willReturn(friendship);

        // when
        FriendshipResponse response = friendService.sendFriendRequest(cognitoSub, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getFriend().getCognitoSub()).isEqualTo(friendCognitoSub);
        assertThat(response.getStatus()).isEqualTo(FriendshipStatus.PENDING);

        then(friendshipRepository).should(times(1)).save(any(Friendship.class));
    }

    @Test
    @DisplayName("친구 요청 실패 - 자기 자신에게 요청")
    void test_sendFriendRequest_SelfRequest_ShouldThrowException() {
        // given
        FriendRequestRequest request = FriendRequestRequest.builder()
                .friendCognitoSub(cognitoSub)
                .build();

        // when & then
        assertThatThrownBy(() -> friendService.sendFriendRequest(cognitoSub, request))
                .isInstanceOf(SelfFriendshipException.class);

        then(friendshipRepository).should(never()).save(any(Friendship.class));
    }

    @Test
    @DisplayName("친구 요청 실패 - 이미 친구 관계 존재")
    void test_sendFriendRequest_AlreadyExists_ShouldThrowException() {
        // given
        FriendRequestRequest request = FriendRequestRequest.builder()
                .friendCognitoSub(friendCognitoSub)
                .build();

        given(userRepository.findByCognitoSub(friendCognitoSub))
                .willReturn(Optional.of(friendUser));
        given(friendshipRepository.existsFriendshipBetween(cognitoSub, friendCognitoSub))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> friendService.sendFriendRequest(cognitoSub, request))
                .isInstanceOf(FriendshipAlreadyExistsException.class);

        then(friendshipRepository).should(never()).save(any(Friendship.class));
    }

    @Test
    @DisplayName("친구 요청 수락 성공 - 양방향 관계 생성")
    void test_acceptFriendRequest_Success_ShouldCreateBidirectionalRelationship() {
        // given
        Long requestId = 1L;
        Friendship pendingFriendship = Friendship.builder()
                .id(requestId)
                .userCognitoSub(friendCognitoSub)
                .friendCognitoSub(cognitoSub)
                .status(FriendshipStatus.PENDING)
                .build();

        given(friendshipRepository.findById(requestId))
                .willReturn(Optional.of(pendingFriendship));
        given(friendshipRepository.save(any(Friendship.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        MessageResponse response = friendService.acceptFriendRequest(cognitoSub, requestId);

        // then
        assertThat(response.getMessage()).contains("수락");

        // 2번 save 호출: 기존 관계 업데이트 + 역방향 관계 생성
        then(friendshipRepository).should(times(2)).save(any(Friendship.class));
    }

    @Test
    @DisplayName("친구 요청 수락 실패 - 요청 수신자가 아님")
    void test_acceptFriendRequest_NotRecipient_ShouldThrowException() {
        // given
        Long requestId = 1L;
        String otherCognitoSub = "other-cognito-sub-789";
        Friendship otherUserRequest = Friendship.builder()
                .id(requestId)
                .userCognitoSub(friendCognitoSub)
                .friendCognitoSub(otherCognitoSub) // 다른 사용자에게 보낸 요청
                .status(FriendshipStatus.PENDING)
                .build();

        given(friendshipRepository.findById(requestId))
                .willReturn(Optional.of(otherUserRequest));

        // when & then
        assertThatThrownBy(() -> friendService.acceptFriendRequest(cognitoSub, requestId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
    }

    @Test
    @DisplayName("친구 요청 거절 성공")
    void test_rejectFriendRequest_Success_ShouldDeleteFriendship() {
        // given
        Long requestId = 1L;
        Friendship pendingFriendship = Friendship.builder()
                .id(requestId)
                .userCognitoSub(friendCognitoSub)
                .friendCognitoSub(cognitoSub)
                .status(FriendshipStatus.PENDING)
                .build();

        given(friendshipRepository.findById(requestId))
                .willReturn(Optional.of(pendingFriendship));

        // when
        MessageResponse response = friendService.rejectFriendRequest(cognitoSub, requestId);

        // then
        assertThat(response.getMessage()).contains("거절");
        then(friendshipRepository).should(times(1)).delete(pendingFriendship);
    }

    @Test
    @DisplayName("받은 친구 요청 목록 조회")
    void test_getPendingRequests_Success_ShouldReturnPendingList() {
        // given
        Friendship request1 = Friendship.builder()
                .id(1L)
                .userCognitoSub(friendCognitoSub)
                .friendCognitoSub(cognitoSub)
                .status(FriendshipStatus.PENDING)
                .build();

        given(friendshipRepository.findByFriendCognitoSubAndStatus(cognitoSub, FriendshipStatus.PENDING))
                .willReturn(List.of(request1));
        given(userRepository.findByCognitoSub(friendCognitoSub))
                .willReturn(Optional.of(friendUser));

        // when
        List<FriendRequestResponse> requests = friendService.getPendingRequests(cognitoSub);

        // then
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getFromUser().getCognitoSub()).isEqualTo(friendCognitoSub);
    }

    @Test
    @DisplayName("친구 목록 조회")
    void test_getFriends_Success_ShouldReturnAcceptedFriends() {
        // given
        Friendship acceptedFriendship = Friendship.builder()
                .id(1L)
                .userCognitoSub(cognitoSub)
                .friendCognitoSub(friendCognitoSub)
                .status(FriendshipStatus.ACCEPTED)
                .build();

        given(friendshipRepository.findByUserCognitoSubAndStatus(cognitoSub, FriendshipStatus.ACCEPTED))
                .willReturn(List.of(acceptedFriendship));
        given(userRepository.findByCognitoSub(friendCognitoSub))
                .willReturn(Optional.of(friendUser));

        // when
        List<FriendshipResponse> friends = friendService.getFriends(cognitoSub);

        // then
        assertThat(friends).hasSize(1);
        assertThat(friends.get(0).getFriend().getCognitoSub()).isEqualTo(friendCognitoSub);
        assertThat(friends.get(0).getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    @DisplayName("친구 삭제 성공 - 양방향 삭제")
    void test_deleteFriend_Success_ShouldDeleteBidirectionally() {
        // given
        Long friendshipId = 1L;
        Friendship acceptedFriendship = Friendship.builder()
                .id(friendshipId)
                .userCognitoSub(cognitoSub)
                .friendCognitoSub(friendCognitoSub)
                .status(FriendshipStatus.ACCEPTED)
                .build();

        given(friendshipRepository.findById(friendshipId))
                .willReturn(Optional.of(acceptedFriendship));

        // when
        MessageResponse response = friendService.deleteFriend(cognitoSub, friendshipId);

        // then
        assertThat(response.getMessage()).contains("삭제");

        // 순방향 + 역방향 삭제
        then(friendshipRepository).should(times(1)).delete(acceptedFriendship);
        then(friendshipRepository).should(times(1))
                .deleteByUserCognitoSubAndFriendCognitoSub(friendCognitoSub, cognitoSub);
    }

    @Test
    @DisplayName("사용자 차단 성공")
    void test_blockUser_Success_ShouldBlockUser() {
        // given
        given(userRepository.findByCognitoSub(friendCognitoSub))
                .willReturn(Optional.of(friendUser));
        given(friendshipRepository.findByUserCognitoSubAndFriendCognitoSub(cognitoSub, friendCognitoSub))
                .willReturn(Optional.empty());
        given(friendshipRepository.save(any(Friendship.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        MessageResponse response = friendService.blockUser(cognitoSub, friendCognitoSub);

        // then
        assertThat(response.getMessage()).contains("차단");
        then(friendshipRepository).should(times(1)).save(any(Friendship.class));
        then(friendshipRepository).should(times(1))
                .deleteByUserCognitoSubAndFriendCognitoSub(friendCognitoSub, cognitoSub);
    }

    @Test
    @DisplayName("사용자 차단 실패 - 자기 자신")
    void test_blockUser_SelfBlock_ShouldThrowException() {
        // when & then
        assertThatThrownBy(() -> friendService.blockUser(cognitoSub, cognitoSub))
                .isInstanceOf(SelfFriendshipException.class);
    }

    @Test
    @DisplayName("사용자 검색 - 차단한 사용자 제외")
    void test_searchUsers_ExcludesBlockedUsers_ShouldFilterBlockedUsers() {
        // given
        String query = "user";
        int limit = 10;
        String blockedCognitoSub = "blocked-cognito-sub-789";

        User user3 = User.builder()
                .id(3L)
                .email("user3@example.com")
                .name("사용자3")
                .cognitoSub(blockedCognitoSub)
                .build();

        given(friendshipRepository.findBlockedCognitoSubs(cognitoSub))
                .willReturn(List.of(blockedCognitoSub)); // user3을 차단
        given(userRepository.findAll())
                .willReturn(List.of(currentUser, friendUser, user3));
        given(friendshipRepository.findByUserCognitoSubAndFriendCognitoSub(cognitoSub, friendCognitoSub))
                .willReturn(Optional.empty());

        // when
        List<UserSummaryDto> results = friendService.searchUsers(cognitoSub, query, limit);

        // then
        assertThat(results).hasSize(1); // user3 제외, 본인 제외
        assertThat(results.get(0).getCognitoSub()).isEqualTo(friendCognitoSub);
    }
}
