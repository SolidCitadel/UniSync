package com.unisync.scheduleservice.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 환경변수 로드 확인 테스트
 *
 * 목적: Gradle dotenv 플러그인이 .env.local 파일을 제대로 로드하는지 확인
 *
 * 실행 조건:
 * - 프로젝트 루트에 .env.local 파일이 존재해야 함
 * - Gradle이 환경변수를 주입해야 함
 */
@SpringBootTest
@ActiveProfiles("integration")
class EnvironmentVariablesTest {

    @Value("${SCHEDULE_SERVICE_DATABASE_URL:#{null}}")
    private String databaseUrl;

    @Value("${SQS_TASK_CREATION_QUEUE:#{null}}")
    private String taskCreationQueue;

    @Test
    void shouldLoadEnvironmentVariablesFromDotenvFile() {
        // .env.local 파일에서 로드된 환경변수 확인
        assertThat(databaseUrl)
                .as("SCHEDULE_SERVICE_DATABASE_URL should be loaded from .env.local")
                .isNotNull()
                .contains("jdbc:mysql");

        assertThat(taskCreationQueue)
                .as("SQS_TASK_CREATION_QUEUE should be loaded from .env.local")
                .isNotNull()
                .isNotEmpty();

        System.out.println("[OK] 환경변수 로드 성공:");
        System.out.println("  - SCHEDULE_SERVICE_DATABASE_URL: " + databaseUrl);
        System.out.println("  - SQS_TASK_CREATION_QUEUE: " + taskCreationQueue);
    }
}
