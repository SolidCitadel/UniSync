package com.unisync.user.common.repository;

import com.unisync.user.common.entity.GroupMember;
import com.unisync.user.common.entity.GroupRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    /**
     * 그룹의 모든 멤버 조회
     *
     * @param groupId 그룹 ID
     * @return 멤버 목록
     */
    List<GroupMember> findByGroupId(Long groupId);

    /**
     * 그룹에서 특정 사용자의 멤버십 조회
     *
     * @param groupId 그룹 ID
     * @param userCognitoSub 사용자 Cognito Sub
     * @return 멤버십
     */
    Optional<GroupMember> findByGroupIdAndUserCognitoSub(Long groupId, String userCognitoSub);

    /**
     * 그룹 멤버십 존재 여부 확인
     *
     * @param groupId 그룹 ID
     * @param userCognitoSub 사용자 Cognito Sub
     * @return true if membership exists
     */
    boolean existsByGroupIdAndUserCognitoSub(Long groupId, String userCognitoSub);

    /**
     * 사용자가 속한 모든 그룹 멤버십 조회
     *
     * @param userCognitoSub 사용자 Cognito Sub
     * @return 멤버십 목록
     */
    List<GroupMember> findByUserCognitoSub(String userCognitoSub);

    /**
     * 그룹의 멤버 수 조회
     *
     * @param groupId 그룹 ID
     * @return 멤버 수
     */
    long countByGroupId(Long groupId);

    /**
     * 그룹에서 특정 역할의 멤버만 조회
     *
     * @param groupId 그룹 ID
     * @param role    역할
     * @return 멤버 목록
     */
    List<GroupMember> findByGroupIdAndRole(Long groupId, GroupRole role);

    /**
     * 그룹 멤버 삭제
     *
     * @param groupId 그룹 ID
     * @param userCognitoSub 사용자 Cognito Sub
     */
    void deleteByGroupIdAndUserCognitoSub(Long groupId, String userCognitoSub);

    /**
     * 그룹의 모든 멤버 삭제
     *
     * @param groupId 그룹 ID
     */
    void deleteByGroupId(Long groupId);

    /**
     * 그룹의 모든 멤버 Cognito Sub 목록 조회
     *
     * @param groupId 그룹 ID
     * @return Cognito Sub 목록
     */
    @Query("SELECT gm.userCognitoSub FROM GroupMember gm WHERE gm.groupId = :groupId")
    List<String> findUserCognitoSubsByGroupId(@Param("groupId") Long groupId);
}
