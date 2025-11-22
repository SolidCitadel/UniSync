package com.unisync.schedule.internal.client;

import com.unisync.schedule.internal.dto.GroupMembershipResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * User-Service Internal API 클라이언트
 */
@Component
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;
    private final String userServiceUrl;

    public UserServiceClient(
            RestTemplate restTemplate,
            @Value("${services.user-service.url}") String userServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.userServiceUrl = userServiceUrl;
    }

    /**
     * 그룹 멤버십 조회
     *
     * @param groupId    그룹 ID
     * @param cognitoSub 사용자 Cognito Sub
     * @return 멤버십 정보 (조회 실패 시 멤버 아님으로 처리)
     */
    public GroupMembershipResponse getMembership(Long groupId, String cognitoSub) {
        String url = userServiceUrl + "/api/internal/groups/" + groupId + "/members/" + cognitoSub;

        try {
            log.debug("User-Service 멤버십 조회: groupId={}, cognitoSub={}", groupId, cognitoSub);
            GroupMembershipResponse response = restTemplate.getForObject(url, GroupMembershipResponse.class);

            if (response == null) {
                log.warn("User-Service 멤버십 응답 null: groupId={}, cognitoSub={}", groupId, cognitoSub);
                return notMember(groupId, cognitoSub);
            }

            log.debug("User-Service 멤버십 조회 결과: groupId={}, cognitoSub={}, isMember={}, role={}",
                    groupId, cognitoSub, response.isMember(), response.getRole());
            return response;
        } catch (RestClientException e) {
            log.error("User-Service 멤버십 조회 실패: groupId={}, cognitoSub={}, error={}",
                    groupId, cognitoSub, e.getMessage());
            return notMember(groupId, cognitoSub);
        }
    }

    private GroupMembershipResponse notMember(Long groupId, String cognitoSub) {
        return GroupMembershipResponse.builder()
                .groupId(groupId)
                .cognitoSub(cognitoSub)
                .isMember(false)
                .role(null)
                .build();
    }
}
