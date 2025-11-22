package com.unisync.user.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 그룹 멤버십
 * 그룹과 사용자의 관계 및 역할을 관리
 * cognitoSub 기반으로 사용자 식별
 */
@Entity
@Table(
    name = "group_members",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_group_user",
        columnNames = {"group_id", "user_cognito_sub"}
    ),
    indexes = {
        @Index(name = "idx_group_id", columnList = "group_id"),
        @Index(name = "idx_user_cognito_sub", columnList = "user_cognito_sub")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 그룹 ID
     */
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /**
     * 사용자 (Cognito Sub)
     */
    @Column(name = "user_cognito_sub", nullable = false, length = 255)
    private String userCognitoSub;

    /**
     * 그룹 내 역할
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GroupRole role = GroupRole.MEMBER;

    /**
     * 가입일
     */
    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;
}
