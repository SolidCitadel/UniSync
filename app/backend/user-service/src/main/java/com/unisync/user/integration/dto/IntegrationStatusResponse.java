package com.unisync.user.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 전체 연동 상태 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "전체 외부 서비스 연동 상태 응답")
public class IntegrationStatusResponse {

    /**
     * Canvas LMS 연동 정보
     */
    @Schema(description = "Canvas LMS 연동 정보")
    private IntegrationInfo canvas;

    /**
     * Google Calendar 연동 정보
     */
    @Schema(description = "Google Calendar 연동 정보 (향후 구현 예정)")
    private IntegrationInfo googleCalendar;

    /**
     * Outlook Calendar 연동 정보
     */
    @Schema(description = "Outlook Calendar 연동 정보 (향후 구현 예정)")
    private IntegrationInfo outlook;
}