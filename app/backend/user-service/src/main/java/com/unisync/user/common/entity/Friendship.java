package com.unisync.user.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 친구 관계
 * 양방향 관계를 명시적으로 저장하여 조회 성능 향상
 * cognitoSub 기반으로 사용자 식별
 */
@Entity
@Table(
    name = "friendships",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_friend",
        columnNames = {"user_cognito_sub", "friend_cognito_sub"}
    ),
    indexes = {
        @Index(name = "idx_user_cognito_sub", columnList = "user_cognito_sub"),
        @Index(name = "idx_friend_cognito_sub", columnList = "friend_cognito_sub"),
        @Index(name = "idx_status", columnList = "status")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 요청자 또는 소유자 (Cognito Sub)
     */
    @Column(name = "user_cognito_sub", nullable = false, length = 255)
    private String userCognitoSub;

    /**
     * 수신자 또는 친구 (Cognito Sub)
     */
    @Column(name = "friend_cognito_sub", nullable = false, length = 255)
    private String friendCognitoSub;

    /**
     * 친구 관계 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 자기 자신에게 친구 요청 불가
     */
    @PrePersist
    @PreUpdate
    private void validateCognitoSubs() {
        if (userCognitoSub != null && userCognitoSub.equals(friendCognitoSub)) {
            throw new IllegalArgumentException("Cannot create friendship with yourself");
        }
    }
}
