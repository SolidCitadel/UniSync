package com.unisync.shared.security;

import com.unisync.shared.security.exception.UnauthorizedException;

import java.util.HashMap;
import java.util.Map;

/**
 * 서비스 간 API 호출 시 API Key를 검증하는 유틸리티 클래스.
 *
 * <p>각 서비스는 고유한 API Key를 가지며, 이 클래스는 API Key를 검증하고
 * 호출자 서비스를 식별합니다.</p>
 *
 * <p>사용 예시:</p>
 * <pre>
 * Map&lt;String, String&gt; apiKeys = Map.of(
 *     "canvas-sync-api-key", "canvas-sync-lambda",
 *     "llm-api-key", "llm-lambda"
 * );
 * ServiceAuthValidator validator = new ServiceAuthValidator(apiKeys);
 * String caller = validator.validateAndGetCaller("canvas-sync-api-key");
 * // caller = "canvas-sync-lambda"
 * </pre>
 */
public class ServiceAuthValidator {

    private final Map<String, String> apiKeyToServiceName;

    /**
     * API Key와 서비스 이름의 매핑을 받아 초기화합니다.
     *
     * @param apiKeyToServiceName API Key → 서비스 이름 매핑 (예: {"key123" → "canvas-sync-lambda"})
     */
    public ServiceAuthValidator(Map<String, String> apiKeyToServiceName) {
        this.apiKeyToServiceName = new HashMap<>(apiKeyToServiceName);
    }

    /**
     * API Key를 검증하고 호출자 서비스 이름을 반환합니다.
     *
     * @param apiKey 요청 헤더의 API Key
     * @return 서비스 이름 (예: "canvas-sync-lambda")
     * @throws UnauthorizedException API Key가 유효하지 않은 경우
     */
    public String validateAndGetCaller(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UnauthorizedException("API Key is required");
        }

        String serviceName = apiKeyToServiceName.get(apiKey);
        if (serviceName == null) {
            throw new UnauthorizedException("Invalid API Key");
        }

        return serviceName;
    }

    /**
     * API Key가 유효한지 확인합니다.
     *
     * @param apiKey 검증할 API Key
     * @return 유효하면 true, 그렇지 않으면 false
     */
    public boolean isValid(String apiKey) {
        return apiKey != null && !apiKey.isBlank() && apiKeyToServiceName.containsKey(apiKey);
    }
}