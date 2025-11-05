package com.unisync.user.integration.dto;

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
public class IntegrationStatusResponse {

    /**
     * Canvas LMS 연동 정보
     */
    private IntegrationInfo canvas;

    /**
     * Google Calendar 연동 정보
     */
    private IntegrationInfo googleCalendar;

    /**
     * Outlook Calendar 연동 정보
     */
    private IntegrationInfo outlook;
}