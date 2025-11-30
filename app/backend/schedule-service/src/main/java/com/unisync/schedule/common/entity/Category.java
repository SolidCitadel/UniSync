package com.unisync.schedule.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "categories", indexes = {
    @Index(name = "idx_cognito_sub", columnList = "cognito_sub"),
    @Index(name = "idx_group_id", columnList = "group_id"),
    @Index(name = "idx_source", columnList = "source_type, source_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_cognito_sub_name", columnNames = {"cognito_sub", "name"}),
    @UniqueConstraint(name = "uk_user_source", columnNames = {"cognito_sub", "source_type", "source_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "cognito_sub", length = 255)
    private String cognitoSub;

    @Column(name = "group_id")
    private Long groupId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 7)
    private String color; // HEX color code (#RRGGBB)

    @Column(length = 50)
    private String icon;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    /**
     * 외부 시스템 타입 (Phase 1.1: 과목별 카테고리 자동 생성)
     * 예: "CANVAS_COURSE", "GOOGLE_CALENDAR"
     */
    @Column(name = "source_type", length = 50)
    private String sourceType;

    /**
     * 외부 시스템 ID (Phase 1.1: 과목별 카테고리 중복 방지)
     * 예: courseId "10"
     */
    @Column(name = "source_id", length = 255)
    private String sourceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
