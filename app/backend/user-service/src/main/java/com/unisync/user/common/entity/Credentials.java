package com.unisync.user.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "credentials",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cognito_sub", "provider"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cognito_sub", nullable = false, length = 255)
    private String cognitoSub;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CredentialProvider provider;

    @Column(name = "encrypted_token", nullable = false, length = 512)
    private String encryptedToken;

    /**
     * 외부 서비스 연동 상태
     */
    @Column(name = "is_connected", nullable = false)
    @Builder.Default
    private Boolean isConnected = false;

    /**
     * 외부 서비스의 사용자 ID (Canvas user ID, Google account ID 등)
     */
    @Column(name = "external_user_id", length = 255)
    private String externalUserId;

    /**
     * 외부 서비스의 사용자명 (Canvas login_id, Google email 등)
     */
    @Column(name = "external_username", length = 255)
    private String externalUsername;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_validated_at")
    private LocalDateTime lastValidatedAt;

    /**
     * 마지막 동기화 시간 (Lambda에서 업데이트)
     */
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}