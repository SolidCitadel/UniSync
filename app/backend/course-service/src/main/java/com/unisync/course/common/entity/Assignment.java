package com.unisync.course.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Assignment Entity - Canvas 과제 정보
 */
@Entity
@Table(name = "assignments", indexes = {
    @Index(name = "idx_canvas_assignment_id", columnList = "canvas_assignment_id"),
    @Index(name = "idx_course_id", columnList = "course_id"),
    @Index(name = "idx_due_at", columnList = "due_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Canvas Assignment ID (UNIQUE)
     */
    @Column(name = "canvas_assignment_id", unique = true, nullable = false)
    private Long canvasAssignmentId;

    /**
     * 소속 과목
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /**
     * 과제 제목
     */
    @Column(nullable = false)
    private String title;

    /**
     * 과제 설명
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 마감일
     */
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    /**
     * 배점
     */
    @Column(name = "points_possible")
    private Integer pointsPossible;

    /**
     * 제출 유형 (예: "online_upload,online_text_entry")
     */
    @Column(name = "submission_types")
    private String submissionTypes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Canvas API에서 받은 데이터로 업데이트
     */
    public void updateFromCanvas(String title,
                                 String description,
                                 LocalDateTime dueAt,
                                 Integer pointsPossible,
                                 String submissionTypes) {
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.pointsPossible = pointsPossible;
        this.submissionTypes = submissionTypes;
    }
}