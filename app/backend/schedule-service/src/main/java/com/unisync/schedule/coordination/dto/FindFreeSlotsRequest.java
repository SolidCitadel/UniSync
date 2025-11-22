package com.unisync.schedule.coordination.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * 공강 시간 찾기 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindFreeSlotsRequest {

    @NotNull(message = "그룹 ID는 필수입니다")
    @Positive(message = "그룹 ID는 양수여야 합니다")
    private Long groupId;

    /**
     * 선택된 멤버 cognitoSub 목록 (optional)
     * null이면 전체 그룹 멤버
     */
    private List<String> userIds;

    @NotNull(message = "시작일은 필수입니다")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "날짜 형식은 YYYY-MM-DD여야 합니다")
    private String startDate;

    @NotNull(message = "종료일은 필수입니다")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "날짜 형식은 YYYY-MM-DD여야 합니다")
    private String endDate;

    @NotNull(message = "최소 지속 시간은 필수입니다")
    @Min(value = 1, message = "최소 지속 시간은 1분 이상이어야 합니다")
    private Integer minDurationMinutes;

    /**
     * 근무/활동 시간 시작 (optional)
     * 형식: "HH:mm" (예: "09:00")
     */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime workingHoursStart;

    /**
     * 근무/활동 시간 종료 (optional)
     * 형식: "HH:mm" (예: "18:00")
     */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime workingHoursEnd;

    /**
     * 요일 필터 (optional)
     * 1=월, 2=화, 3=수, 4=목, 5=금, 6=토, 7=일
     */
    private List<Integer> daysOfWeek;
}
