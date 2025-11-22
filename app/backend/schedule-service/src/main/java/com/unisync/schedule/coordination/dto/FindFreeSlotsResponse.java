package com.unisync.schedule.coordination.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 공강 시간 찾기 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindFreeSlotsResponse {

    private Long groupId;

    private String groupName;

    private Integer memberCount;

    private SearchPeriodDto searchPeriod;

    private List<FreeSlotDto> freeSlots;

    private Integer totalFreeSlotsFound;

    /**
     * Builder helper to set totalFreeSlotsFound from freeSlots size
     */
    public static class FindFreeSlotsResponseBuilder {
        public FindFreeSlotsResponseBuilder freeSlots(List<FreeSlotDto> freeSlots) {
            this.freeSlots = freeSlots;
            this.totalFreeSlotsFound = freeSlots != null ? freeSlots.size() : 0;
            return this;
        }
    }
}
