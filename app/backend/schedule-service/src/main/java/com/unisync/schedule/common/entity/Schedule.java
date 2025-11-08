package com.unisync.schedule.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedules", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_group_id", columnList = "group_id"),
    @Index(name = "idx_category_id", columnList = "category_id"),
    @Index(name = "idx_start_time", columnList = "start_time"),
    @Index(name = "idx_end_time", columnList = "end_time"),
    @Index(name = "idx_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String location;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "is_all_day", nullable = false)
    private Boolean isAllDay = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleStatus status = ScheduleStatus.TODO;

    @Column(name = "recurrence_rule", length = 255)
    private String recurrenceRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleSource source = ScheduleSource.USER;

    @Column(name = "source_id", length = 255)
    private String sourceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ScheduleStatus {
        TODO, IN_PROGRESS, DONE
    }

    public enum ScheduleSource {
        USER, CANVAS, GOOGLE_CALENDAR, TODOIST
    }
}
