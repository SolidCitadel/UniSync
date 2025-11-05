package com.unisync.course.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unisync.course.common.entity.Course;
import com.unisync.course.common.entity.Enrollment;
import com.unisync.course.common.repository.CourseRepository;
import com.unisync.course.common.repository.EnrollmentRepository;
import com.unisync.shared.dto.sqs.CourseEnrollmentEvent;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Course Enrollment SQS 통합 테스트
 * LocalStack과 MySQL을 Testcontainers로 띄워서 실제 SQS 메시지 송수신 테스트
 */
@SpringBootTest
@Testcontainers
class CourseEnrollmentIntegrationTest {

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
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private SqsClient sqsClient;
    private String courseEnrollmentQueueUrl;
    private String assignmentSyncQueueUrl;
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
        enrollmentRepository.deleteAll();
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
        CreateQueueResponse courseEnrollmentQueue = sqsClient.createQueue(
            CreateQueueRequest.builder()
                .queueName("course-enrollment-queue")
                .build()
        );
        courseEnrollmentQueueUrl = courseEnrollmentQueue.queueUrl();

        CreateQueueResponse assignmentSyncQueue = sqsClient.createQueue(
            CreateQueueRequest.builder()
                .queueName("assignment-sync-needed-queue")
                .build()
        );
        assignmentSyncQueueUrl = assignmentSyncQueue.queueUrl();

        // 큐 초기화 (이전 테스트의 메시지 제거)
        sqsClient.purgeQueue(PurgeQueueRequest.builder()
            .queueUrl(courseEnrollmentQueueUrl)
            .build());
        sqsClient.purgeQueue(PurgeQueueRequest.builder()
            .queueUrl(assignmentSyncQueueUrl)
            .build());

        // ObjectMapper 설정
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("새 Course 등록 시 Course 생성, Enrollment 생성 (Leader=true), Assignment 동기화 이벤트 발행")
    void testNewCourseEnrollment_ShouldCreateCourseAndEnrollmentAsLeader() throws Exception {
        // given: 첫 번째 사용자의 Course Enrollment 이벤트
        CourseEnrollmentEvent event = CourseEnrollmentEvent.builder()
            .userId(1L)
            .canvasCourseId(789L)
            .courseName("Spring Boot 고급")
            .courseCode("CS301")
            .workflowState("available")
            .startAt(LocalDateTime.of(2025, 9, 1, 0, 0))
            .endAt(LocalDateTime.of(2025, 12, 31, 23, 59))
            .publishedAt(LocalDateTime.now())
            .build();

        // when: SQS에 메시지 발행
        String messageBody = objectMapper.writeValueAsString(event);
        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(courseEnrollmentQueueUrl)
            .messageBody(messageBody)
            .build());

        // then: Course가 생성됨
        await()
            .atMost(java.time.Duration.ofSeconds(10))
            .pollInterval(java.time.Duration.ofMillis(500))
            .untilAsserted(() -> {
                Optional<Course> savedCourse = courseRepository.findByCanvasCourseId(789L);
                assertThat(savedCourse).isPresent();
                assertThat(savedCourse.get().getName()).isEqualTo("Spring Boot 고급");
                assertThat(savedCourse.get().getCourseCode()).isEqualTo("CS301");
            });

        // then: Enrollment가 생성되고 Leader 플래그가 true
        Course course = courseRepository.findByCanvasCourseId(789L).get();
        Optional<Enrollment> enrollment = enrollmentRepository.findByUserIdAndCourseId(1L, course.getId());
        assertThat(enrollment).isPresent();
        assertThat(enrollment.get().getIsSyncLeader()).isTrue();

        // then: assignment-sync-needed-queue에 메시지가 발행됨
        ReceiveMessageResponse messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
            .queueUrl(assignmentSyncQueueUrl)
            .maxNumberOfMessages(1)
            .build());

        assertThat(messages.messages()).hasSize(1);
        String receivedBody = messages.messages().get(0).body();
        assertThat(receivedBody).contains("\"courseId\"");
        assertThat(receivedBody).contains("\"canvasCourseId\":789");
        assertThat(receivedBody).contains("\"leaderUserId\":1");
    }

    @Test
    @DisplayName("기존 Course에 두 번째 사용자 등록 시 Enrollment만 생성 (Leader=false), Assignment 동기화 이벤트 발행 안 함")
    void testExistingCourseEnrollment_ShouldCreateEnrollmentOnlyAsNonLeader() throws Exception {
        // given: 기존 Course와 첫 번째 사용자의 Enrollment
        Course existingCourse = Course.builder()
            .canvasCourseId(789L)
            .name("Spring Boot 고급")
            .courseCode("CS301")
            .startAt(LocalDateTime.of(2025, 9, 1, 0, 0))
            .endAt(LocalDateTime.of(2025, 12, 31, 23, 59))
            .build();
        courseRepository.save(existingCourse);

        Enrollment firstUserEnrollment = Enrollment.builder()
            .userId(1L)
            .course(existingCourse)
            .isSyncLeader(true)
            .build();
        enrollmentRepository.save(firstUserEnrollment);

        // given: 두 번째 사용자의 Course Enrollment 이벤트
        CourseEnrollmentEvent event = CourseEnrollmentEvent.builder()
            .userId(2L)
            .canvasCourseId(789L)
            .courseName("Spring Boot 고급")
            .courseCode("CS301")
            .workflowState("available")
            .startAt(LocalDateTime.of(2025, 9, 1, 0, 0))
            .endAt(LocalDateTime.of(2025, 12, 31, 23, 59))
            .publishedAt(LocalDateTime.now())
            .build();

        // when: SQS에 메시지 발행
        String messageBody = objectMapper.writeValueAsString(event);
        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(courseEnrollmentQueueUrl)
            .messageBody(messageBody)
            .build());

        // then: Enrollment가 생성되고 Leader 플래그가 false
        await()
            .atMost(java.time.Duration.ofSeconds(10))
            .pollInterval(java.time.Duration.ofMillis(500))
            .untilAsserted(() -> {
                Optional<Enrollment> enrollment = enrollmentRepository
                    .findByUserIdAndCourseId(2L, existingCourse.getId());
                assertThat(enrollment).isPresent();
                assertThat(enrollment.get().getIsSyncLeader()).isFalse();
            });

        // then: Course는 여전히 1개만 존재
        assertThat(courseRepository.count()).isEqualTo(1);

        // then: assignment-sync-needed-queue에 메시지가 발행되지 않음
        Thread.sleep(2000); // 메시지 처리 대기
        ReceiveMessageResponse messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
            .queueUrl(assignmentSyncQueueUrl)
            .maxNumberOfMessages(1)
            .build());

        assertThat(messages.messages()).isEmpty();
    }

    @Test
    @DisplayName("중복된 Enrollment 생성 시 무시됨")
    void testDuplicateEnrollment_ShouldBeIgnored() throws Exception {
        // given: 기존 Course와 Enrollment
        Course course = Course.builder()
            .canvasCourseId(789L)
            .name("Spring Boot 고급")
            .courseCode("CS301")
            .build();
        courseRepository.save(course);

        Enrollment existingEnrollment = Enrollment.builder()
            .userId(1L)
            .course(course)
            .isSyncLeader(true)
            .build();
        enrollmentRepository.save(existingEnrollment);

        long initialEnrollmentCount = enrollmentRepository.count();

        // given: 동일한 userId + courseId로 메시지 생성
        CourseEnrollmentEvent duplicateEvent = CourseEnrollmentEvent.builder()
            .userId(1L)
            .canvasCourseId(789L)
            .courseName("Spring Boot 고급")
            .courseCode("CS301")
            .workflowState("available")
            .publishedAt(LocalDateTime.now())
            .build();

        // when: SQS에 중복 메시지 발행
        String messageBody = objectMapper.writeValueAsString(duplicateEvent);
        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(courseEnrollmentQueueUrl)
            .messageBody(messageBody)
            .build());

        // then: Enrollment 개수가 증가하지 않음
        Thread.sleep(2000); // 메시지 처리 대기
        long finalEnrollmentCount = enrollmentRepository.count();
        assertThat(finalEnrollmentCount).isEqualTo(initialEnrollmentCount);
    }

    @Test
    @DisplayName("여러 Course Enrollment 이벤트 동시 처리")
    void testMultipleCourseEnrollments_ShouldBeProcessedCorrectly() throws Exception {
        // given: 3개의 Course Enrollment 이벤트
        CourseEnrollmentEvent event1 = CourseEnrollmentEvent.builder()
            .userId(1L)
            .canvasCourseId(100L)
            .courseName("Course 1")
            .courseCode("CS100")
            .workflowState("available")
            .publishedAt(LocalDateTime.now())
            .build();

        CourseEnrollmentEvent event2 = CourseEnrollmentEvent.builder()
            .userId(1L)
            .canvasCourseId(200L)
            .courseName("Course 2")
            .courseCode("CS200")
            .workflowState("available")
            .publishedAt(LocalDateTime.now())
            .build();

        CourseEnrollmentEvent event3 = CourseEnrollmentEvent.builder()
            .userId(1L)
            .canvasCourseId(300L)
            .courseName("Course 3")
            .courseCode("CS300")
            .workflowState("available")
            .publishedAt(LocalDateTime.now())
            .build();

        // when: SQS에 3개의 메시지 발행
        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(courseEnrollmentQueueUrl)
            .messageBody(objectMapper.writeValueAsString(event1))
            .build());

        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(courseEnrollmentQueueUrl)
            .messageBody(objectMapper.writeValueAsString(event2))
            .build());

        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(courseEnrollmentQueueUrl)
            .messageBody(objectMapper.writeValueAsString(event3))
            .build());

        // then: 3개의 Course가 생성됨
        await()
            .atMost(java.time.Duration.ofSeconds(15))
            .pollInterval(java.time.Duration.ofMillis(500))
            .untilAsserted(() -> {
                assertThat(courseRepository.count()).isEqualTo(3);
            });

        // then: 3개의 Enrollment가 모두 Leader
        List<Enrollment> enrollments = enrollmentRepository.findAllByUserId(1L);
        assertThat(enrollments).hasSize(3);
        assertThat(enrollments).allMatch(Enrollment::getIsSyncLeader);
    }
}