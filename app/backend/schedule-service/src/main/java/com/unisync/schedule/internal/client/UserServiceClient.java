package com.unisync.schedule.internal.client;

import com.unisync.schedule.internal.dto.GroupMembershipResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

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

    /**
     * 그룹의 모든 멤버 cognitoSub 목록 조회
     *
     * @param groupId 그룹 ID
     * @return cognitoSub 목록 (조회 실패 시 빈 리스트)
     */
    public List<String> getGroupMemberCognitoSubs(Long groupId) {
        String url = userServiceUrl + "/api/internal/groups/" + groupId + "/members/cognito-subs";

        try {
            log.debug("User-Service 그룹 멤버 목록 조회: groupId={}", groupId);
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );

            List<String> cognitoSubs = response.getBody();
            log.debug("User-Service 그룹 멤버 목록 조회 결과: groupId={}, memberCount={}",
                    groupId, cognitoSubs != null ? cognitoSubs.size() : 0);
            return cognitoSubs != null ? cognitoSubs : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("User-Service 그룹 멤버 목록 조회 실패: groupId={}, error={}",
                    groupId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 사용자가 속한 그룹 ID 목록 조회
     *
     * @param cognitoSub 사용자 Cognito Sub
     * @return 그룹 ID 목록 (조회 실패 시 빈 리스트)
     */
    public List<Long> getUserGroupIds(String cognitoSub) {
        String url = userServiceUrl + "/api/internal/groups/memberships/" + cognitoSub;

        try {
            log.debug("User-Service 사용자 그룹 목록 조회: cognitoSub={}", cognitoSub);
            ResponseEntity<List<Long>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Long>>() {}
            );
            List<Long> groupIds = response.getBody();
            log.debug("User-Service 사용자 그룹 목록 조회 결과: cognitoSub={}, groupCount={}",
                    cognitoSub, groupIds != null ? groupIds.size() : 0);
            return groupIds != null ? groupIds : Collections.emptyList();
        } catch (RestClientException e) {
            log.error("User-Service 사용자 그룹 목록 조회 실패: cognitoSub={}, error={}", cognitoSub, e.getMessage());
            return Collections.emptyList();
        }
    }
}
