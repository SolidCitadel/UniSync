package com.unisync.course.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Course Entity - Canvas 과목 정보
 */
@Entity
@Table(name = "courses", indexes = {
    @Index(name = "idx_canvas_course_id", columnList = "canvas_course_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Canvas Course ID (UNIQUE)
     */
    @Column(name = "canvas_course_id", unique = true, nullable = false)
    private Long canvasCourseId;

    /**
     * 과목명
     */
    @Column(nullable = false)
    private String name;

    /**
     * 과목 코드 (예: "CS101")
     */
    @Column(name = "course_code")
    private String courseCode;

    /**
     * 과목 설명
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 시작일
     */
    @Column(name = "start_at")
    private LocalDateTime startAt;

    /**
     * 종료일
     */
    @Column(name = "end_at")
    private LocalDateTime endAt;

    /**
     * 과제 목록
     */
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Assignment> assignments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}