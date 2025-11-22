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
 * 그룹
 * 여러 사용자가 함께 일정과 할일을 관리하는 단위
 * cognitoSub 기반으로 소유자 식별
 */
@Entity
@Table(
    name = "`groups`",
    indexes = {
        @Index(name = "idx_owner_cognito_sub", columnList = "owner_cognito_sub")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 그룹명
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 그룹 설명
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 그룹 소유자 (Cognito Sub)
     */
    @Column(name = "owner_cognito_sub", nullable = false, length = 255)
    private String ownerCognitoSub;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
