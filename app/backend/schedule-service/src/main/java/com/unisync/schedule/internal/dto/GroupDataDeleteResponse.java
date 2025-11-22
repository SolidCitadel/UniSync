package com.unisync.schedule.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 그룹 데이터 삭제 응답 (Internal API)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "그룹 데이터 삭제 응답 (Internal API)")
public class GroupDataDeleteResponse {

    @Schema(description = "그룹 ID", example = "1")
    private Long groupId;

    @Schema(description = "삭제된 일정 수", example = "5")
    private long deletedSchedules;

    @Schema(description = "삭제된 할일 수", example = "10")
    private long deletedTodos;

    @Schema(description = "삭제된 카테고리 수", example = "3")
    private long deletedCategories;

    @Schema(description = "성공 여부", example = "true")
    private boolean success;

    public static GroupDataDeleteResponse success(Long groupId, long schedules, long todos, long categories) {
        return GroupDataDeleteResponse.builder()
                .groupId(groupId)
                .deletedSchedules(schedules)
                .deletedTodos(todos)
                .deletedCategories(categories)
                .success(true)
                .build();
    }

    public static GroupDataDeleteResponse noData(Long groupId) {
        return GroupDataDeleteResponse.builder()
                .groupId(groupId)
                .deletedSchedules(0)
                .deletedTodos(0)
                .deletedCategories(0)
                .success(true)
                .build();
    }
}
