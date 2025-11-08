package com.unisync.schedule.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "categories", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_group_id", columnList = "group_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_name", columnNames = {"user_id", "name"})
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

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 7)
    private String color; // HEX color code (#RRGGBB)

    @Column(length = 50)
    private String icon;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
