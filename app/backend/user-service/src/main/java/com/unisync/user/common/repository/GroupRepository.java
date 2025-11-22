package com.unisync.user.common.repository;

import com.unisync.user.common.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * 소유자로 그룹 목록 조회
     *
     * @param ownerCognitoSub 소유자 Cognito Sub
     * @return 그룹 목록
     */
    List<Group> findByOwnerCognitoSub(String ownerCognitoSub);

    /**
     * 사용자가 속한 모든 그룹 조회 (GroupMember 조인)
     *
     * @param userCognitoSub 사용자 Cognito Sub
     * @return 그룹 목록
     */
    @Query("SELECT g FROM Group g " +
           "JOIN GroupMember gm ON g.id = gm.groupId " +
           "WHERE gm.userCognitoSub = :userCognitoSub")
    List<Group> findGroupsByMemberCognitoSub(@Param("userCognitoSub") String userCognitoSub);
}
