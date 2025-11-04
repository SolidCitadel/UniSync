package com.unisync.user.credentials.service;

import com.unisync.user.credentials.exception.InvalidCanvasTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Canvas LMS API 클라이언트
 */
@Slf4j
@Component
public class CanvasApiClient {

    private final String canvasBaseUrl;
    private final RestTemplate restTemplate;

    public CanvasApiClient(
            @Value("${canvas.base-url}") String canvasBaseUrl,
            RestTemplate restTemplate
    ) {
        this.canvasBaseUrl = canvasBaseUrl;
        this.restTemplate = restTemplate;
    }

    /**
     * Canvas API 토큰의 유효성을 검증합니다.
     * GET /api/v1/users/self 를 호출하여 토큰이 유효한지 확인합니다.
     *
     * @param canvasToken 검증할 Canvas API 토큰
     * @throws InvalidCanvasTokenException 토큰이 유효하지 않은 경우
     */
    public void validateToken(String canvasToken) {
        String url = canvasBaseUrl + "/api/v1/users/self";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + canvasToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Canvas token validated successfully");
            } else {
                throw new InvalidCanvasTokenException("Canvas token validation failed with status: " + response.getStatusCode());
            }

        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("Canvas token is unauthorized: {}", e.getMessage());
            throw new InvalidCanvasTokenException("Invalid Canvas token: Unauthorized");
        } catch (HttpClientErrorException e) {
            log.error("Canvas token validation failed: {}", e.getMessage());
            throw new InvalidCanvasTokenException("Invalid Canvas token: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during Canvas token validation: {}", e.getMessage(), e);
            throw new InvalidCanvasTokenException("Failed to validate Canvas token: " + e.getMessage(), e);
        }
    }
}