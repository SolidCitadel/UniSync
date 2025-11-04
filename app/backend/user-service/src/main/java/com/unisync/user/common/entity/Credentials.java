package com.unisync.user.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "credentials",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CredentialProvider provider;

    @Column(name = "encrypted_token", nullable = false, length = 512)
    private String encryptedToken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_validated_at")
    private LocalDateTime lastValidatedAt;
}