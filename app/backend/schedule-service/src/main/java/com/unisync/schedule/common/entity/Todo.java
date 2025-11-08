package com.unisync.schedule.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "todos", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_group_id", columnList = "group_id"),
    @Index(name = "idx_category_id", columnList = "category_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_priority", columnList = "priority"),
    @Index(name = "idx_due_date", columnList = "due_date"),
    @Index(name = "idx_parent_todo_id", columnList = "parent_todo_id"),
    @Index(name = "idx_schedule_id", columnList = "schedule_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Todo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "todo_id")
    private Long todoId;

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

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TodoStatus status = TodoStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TodoPriority priority = TodoPriority.MEDIUM;

    @Column(name = "progress_percentage", nullable = false)
    private Integer progressPercentage = 0;

    @Column(name = "parent_todo_id")
    private Long parentTodoId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "is_ai_generated", nullable = false)
    private Boolean isAiGenerated = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum TodoStatus {
        TODO, IN_PROGRESS, DONE
    }

    public enum TodoPriority {
        LOW, MEDIUM, HIGH, URGENT
    }
}
