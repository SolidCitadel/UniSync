package com.unisync.schedule.coordination.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 검색 기간 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchPeriodDto {

    private String startDate;  // "YYYY-MM-DD"

    private String endDate;    // "YYYY-MM-DD"
}
