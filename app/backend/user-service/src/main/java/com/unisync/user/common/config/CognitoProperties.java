package com.unisync.user.common.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AWS Cognito 설정 프로퍼티
 *
 * application.yml의 aws.cognito 설정을 바인딩하고 필수 값을 검증합니다.
 * 환경 변수가 설정되지 않았거나 유효하지 않은 값일 경우 애플리케이션 시작 시 에러가 발생합니다.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "aws.cognito")
public class CognitoProperties {

    /**
     * Cognito User Pool ID (필수)
     * 환경 변수: COGNITO_USER_POOL_ID
     *
     * LocalStack 사용 시: localstack-init/02-create-cognito.sh 실행 후 생성된 값 사용
     */
    @NotBlank(message = "COGNITO_USER_POOL_ID must be configured. Please check your .env file.")
    private String userPoolId;

    /**
     * Cognito App Client ID (필수)
     * 환경 변수: COGNITO_CLIENT_ID
     *
     * LocalStack 사용 시: localstack-init/02-create-cognito.sh 실행 후 생성된 값 사용
     */
    @NotBlank(message = "COGNITO_CLIENT_ID must be configured. Please check your .env file.")
    private String clientId;

    /**
     * AWS Region (필수)
     * 환경 변수: COGNITO_REGION
     * 기본값: ap-northeast-2 (서울)
     */
    @NotBlank(message = "COGNITO_REGION must be configured. Please check your .env file.")
    private String region;

    /**
     * Cognito Endpoint (선택)
     * 환경 변수: COGNITO_ENDPOINT
     *
     * LocalStack 사용 시: http://localhost:4566
     * 실제 AWS 사용 시: 빈 문자열 또는 설정하지 않음
     */
    private String endpoint;
}