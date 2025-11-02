package com.unisync.course.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unisync.shared.dto.sqs.AssignmentEventMessage;
import com.unisync.course.common.entity.Assignment;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.repository.AssignmentRepository;
import com.unisync.course.common.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Assignment Event SQS 통합 테스트
 * LocalStack과 MySQL을 Testcontainers로 띄워서 실제 SQS 메시지 송수신 테스트
 */
@SpringBootTest
@Testcontainers
class AssignmentEventIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("course_service_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3.0"))
        .withServices(SQS);

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    private SqsClient sqsClient;
    private String queueUrl;
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // MySQL 설정
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        // LocalStack SQS 설정
        registry.add("spring.cloud.aws.sqs.endpoint",
            () -> localStack.getEndpointOverride(SQS).toString());
        registry.add("spring.cloud.aws.region.static", () -> localStack.getRegion());
        registry.add("spring.cloud.aws.credentials.access-key", () -> localStack.getAccessKey());
        registry.add("spring.cloud.aws.credentials.secret-key", () -> localStack.getSecretKey());
    }

    @BeforeEach
    void setUp() {
        // Repository 초기화
        assignmentRepository.deleteAll();
        courseRepository.deleteAll();

        // SQS Client 생성
        sqsClient = SqsClient.builder()
            .endpointOverride(localStack.getEndpointOverride(SQS))
            .region(Region.of(localStack.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())
            ))
            .build();

        // SQS 큐 생성
        CreateQueueResponse response = sqsClient.createQueue(
            CreateQueueRequest.builder()
                .queueName("assignment-events-queue")
                .build()
        );
        queueUrl = response.queueUrl();

        // ObjectMapper 설정
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("SQS 메시지 발행 → Listener 수신 → DB 저장 통합 테스트")
    void testAssignmentEventFlow_ProduceAndConsume() throws Exception {
        // given: Course 먼저 생성
        Course course = Course.builder()
            .canvasCourseId(789L)
            .name("Spring Boot 고급")
            .courseCode("CS301")
            .description("Spring Boot 심화 과정")
            .startAt(LocalDateTime.of(2025, 9, 1, 0, 0))
            .endAt(LocalDateTime.of(2025, 12, 31, 23, 59))
            .build();
        courseRepository.save(course);

        // given: Assignment 이벤트 메시지 생성
        AssignmentEventMessage message = AssignmentEventMessage.builder()
            .eventType("ASSIGNMENT_CREATED")
            .canvasAssignmentId(123456L)
            .canvasCourseId(789L)
            .title("중간고사 프로젝트")
            .description("Spring Boot로 REST API 구현")
            .dueAt(LocalDateTime.of(2025, 11, 15, 23, 59, 59))
            .pointsPossible(100)
            .submissionTypes("online_upload")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // when: SQS에 메시지 발행 (Producer 역할)
        String messageBody = objectMapper.writeValueAsString(message);
        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(messageBody)
            .build());

        // then: Listener가 메시지를 수신하고 DB에 저장할 때까지 대기
        await()
            .atMost(java.time.Duration.ofSeconds(10))
            .pollInterval(java.time.Duration.ofMillis(500))
            .untilAsserted(() -> {
                Optional<Assignment> saved = assignmentRepository
                    .findByCanvasAssignmentId(123456L);
                assertThat(saved).isPresent();
                assertThat(saved.get().getTitle()).isEqualTo("중간고사 프로젝트");
                assertThat(saved.get().getDescription()).isEqualTo("Spring Boot로 REST API 구현");
                assertThat(saved.get().getPointsPossible()).isEqualTo(100);
                // Course는 지연 로딩이므로 별도 검증
            });

        // Course 연관 관계 검증 (별도 트랜잭션)
        Assignment savedAssignment = assignmentRepository.findByCanvasAssignmentId(123456L).get();
        Course savedCourse = courseRepository.findById(savedAssignment.getCourse().getId()).get();
        assertThat(savedCourse.getCanvasCourseId()).isEqualTo(789L);
    }

    @Test
    @DisplayName("SQS 메시지 발행 → Assignment 업데이트 통합 테스트")
    void testAssignmentUpdateFlow_ProduceAndConsume() throws Exception {
        // given: Course와 기존 Assignment 생성
        Course course = Course.builder()
            .canvasCourseId(789L)
            .name("Spring Boot 고급")
            .courseCode("CS301")
            .build();
        courseRepository.save(course);

        Assignment existingAssignment = Assignment.builder()
            .canvasAssignmentId(123456L)
            .course(course)
            .title("기존 제목")
            .description("기존 설명")
            .dueAt(LocalDateTime.of(2025, 11, 10, 23, 59, 59))
            .pointsPossible(80)
            .submissionTypes("online_text_entry")
            .build();
        assignmentRepository.save(existingAssignment);

        // given: Assignment 업데이트 메시지 생성
        AssignmentEventMessage updateMessage = AssignmentEventMessage.builder()
            .eventType("ASSIGNMENT_UPDATED")
            .canvasAssignmentId(123456L)
            .canvasCourseId(789L)
            .title("수정된 제목")
            .description("수정된 설명")
            .dueAt(LocalDateTime.of(2025, 11, 20, 23, 59, 59))
            .pointsPossible(100)
            .submissionTypes("online_upload")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // when: SQS에 업데이트 메시지 발행
        String messageBody = objectMapper.writeValueAsString(updateMessage);
        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(messageBody)
            .build());

        // then: 업데이트된 내용 확인
        await()
            .atMost(java.time.Duration.ofSeconds(10))
            .pollInterval(java.time.Duration.ofMillis(500))
            .untilAsserted(() -> {
                Optional<Assignment> updated = assignmentRepository
                    .findByCanvasAssignmentId(123456L);
                assertThat(updated).isPresent();
                assertThat(updated.get().getTitle()).isEqualTo("수정된 제목");
                assertThat(updated.get().getDescription()).isEqualTo("수정된 설명");
                assertThat(updated.get().getPointsPossible()).isEqualTo(100);
                assertThat(updated.get().getSubmissionTypes()).isEqualTo("online_upload");
            });
    }

    @Test
    @DisplayName("중복된 Assignment 생성 시 무시됨")
    void testDuplicateAssignment_ShouldBeIgnored() throws Exception {
        // given: Course와 기존 Assignment 생성
        Course course = Course.builder()
            .canvasCourseId(789L)
            .name("Spring Boot 고급")
            .courseCode("CS301")
            .build();
        courseRepository.save(course);

        Assignment existingAssignment = Assignment.builder()
            .canvasAssignmentId(123456L)
            .course(course)
            .title("기존 과제")
            .description("기존 설명")
            .dueAt(LocalDateTime.of(2025, 11, 15, 23, 59, 59))
            .pointsPossible(100)
            .submissionTypes("online_upload")
            .build();
        assignmentRepository.save(existingAssignment);

        long initialCount = assignmentRepository.count();

        // given: 동일한 canvasAssignmentId로 메시지 생성
        AssignmentEventMessage duplicateMessage = AssignmentEventMessage.builder()
            .eventType("ASSIGNMENT_CREATED")
            .canvasAssignmentId(123456L)
            .canvasCourseId(789L)
            .title("중복 과제")
            .description("중복 설명")
            .dueAt(LocalDateTime.of(2025, 11, 15, 23, 59, 59))
            .pointsPossible(100)
            .submissionTypes("online_upload")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // when: SQS에 중복 메시지 발행
        String messageBody = objectMapper.writeValueAsString(duplicateMessage);
        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(messageBody)
            .build());

        // then: Assignment 개수가 증가하지 않음
        Thread.sleep(2000); // 메시지 처리 대기
        long finalCount = assignmentRepository.count();
        assertThat(finalCount).isEqualTo(initialCount);

        // 기존 데이터가 유지됨
        Optional<Assignment> assignment = assignmentRepository.findByCanvasAssignmentId(123456L);
        assertThat(assignment).isPresent();
        assertThat(assignment.get().getTitle()).isEqualTo("기존 과제");
    }
}