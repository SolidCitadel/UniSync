package com.unisync.user.common.repository;

import com.unisync.user.common.entity.Friendship;
import com.unisync.user.common.entity.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /**
     * 사용자 cognitoSub와 친구 cognitoSub로 친구 관계 조회
     */
    Optional<Friendship> findByUserCognitoSubAndFriendCognitoSub(String userCognitoSub, String friendCognitoSub);

    /**
     * 친구 목록 조회 (특정 상태)
     *
     * @param userCognitoSub 사용자 Cognito Sub
     * @param status 상태
     * @return 친구 관계 목록
     */
    List<Friendship> findByUserCognitoSubAndStatus(String userCognitoSub, FriendshipStatus status);

    /**
     * 받은 친구 요청 목록 조회 (특정 상태)
     *
     * @param friendCognitoSub 수신자 Cognito Sub
     * @param status 상태
     * @return 친구 요청 목록
     */
    List<Friendship> findByFriendCognitoSubAndStatus(String friendCognitoSub, FriendshipStatus status);

    /**
     * 두 사용자 간 친구 관계 존재 여부 확인 (양방향)
     *
     * @param cognitoSub1 사용자 1 Cognito Sub
     * @param cognitoSub2 사용자 2 Cognito Sub
     * @return true if friendship exists
     */
    @Query("SELECT COUNT(f) > 0 FROM Friendship f " +
           "WHERE (f.userCognitoSub = :cognitoSub1 AND f.friendCognitoSub = :cognitoSub2) " +
           "OR (f.userCognitoSub = :cognitoSub2 AND f.friendCognitoSub = :cognitoSub1)")
    boolean existsFriendshipBetween(@Param("cognitoSub1") String cognitoSub1, @Param("cognitoSub2") String cognitoSub2);

    /**
     * 친구 관계 삭제
     *
     * @param userCognitoSub 사용자 Cognito Sub
     * @param friendCognitoSub 친구 Cognito Sub
     */
    void deleteByUserCognitoSubAndFriendCognitoSub(String userCognitoSub, String friendCognitoSub);

    /**
     * 사용자가 차단한 목록 조회
     *
     * @param userCognitoSub 사용자 Cognito Sub
     * @return 차단된 사용자 Cognito Sub 목록
     */
    @Query("SELECT f.friendCognitoSub FROM Friendship f " +
           "WHERE f.userCognitoSub = :userCognitoSub AND f.status = 'BLOCKED'")
    List<String> findBlockedCognitoSubs(@Param("userCognitoSub") String userCognitoSub);
}
