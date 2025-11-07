package com.unisync.course.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Enrollment Entity - 사용자와 과목 간의 수강 관계
 */
@Entity
@Table(name = "enrollments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_course", columnNames = {"cognito_sub", "course_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Cognito 사용자 ID (User-Service 참조)
     */
    @Column(name = "cognito_sub", nullable = false, length = 255)
    private String cognitoSub;

    /**
     * 과목
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_enrollment_course"))
    private Course course;

    /**
     * 동기화 리더 여부 (이 과목을 처음 등록한 사용자)
     * Leader만 Canvas API를 호출하여 Assignment를 동기화
     */
    @Column(name = "is_sync_leader", nullable = false)
    @Builder.Default
    private Boolean isSyncLeader = false;

    /**
     * 수강 등록 시각
     */
    @CreationTimestamp
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private LocalDateTime enrolledAt;
}