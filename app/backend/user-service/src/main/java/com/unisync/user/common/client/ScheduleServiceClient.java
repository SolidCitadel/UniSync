package com.unisync.user.common.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Schedule-Service Internal API 클라이언트
 */
@Component
@Slf4j
public class ScheduleServiceClient {

    private final RestTemplate restTemplate;
    private final String scheduleServiceUrl;

    public ScheduleServiceClient(
            RestTemplate restTemplate,
            @Value("${services.schedule-service.url}") String scheduleServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.scheduleServiceUrl = scheduleServiceUrl;
    }

    /**
     * 그룹 데이터 삭제 요청
     *
     * @param groupId 그룹 ID
     * @return 성공 여부
     */
    public boolean deleteGroupData(Long groupId) {
        String url = scheduleServiceUrl + "/api/internal/groups/" + groupId + "/data";

        try {
            log.info("Schedule-Service 그룹 데이터 삭제 요청: groupId={}, url={}", groupId, url);
            restTemplate.delete(url);
            log.info("Schedule-Service 그룹 데이터 삭제 완료: groupId={}", groupId);
            return true;
        } catch (RestClientException e) {
            log.error("Schedule-Service 그룹 데이터 삭제 실패: groupId={}, error={}", groupId, e.getMessage());
            return false;
        }
    }
}
