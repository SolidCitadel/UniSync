package com.unisync.schedule.assignment.service;

import com.unisync.schedule.assignment.dto.UserAssignmentsBatchMessage;
import com.unisync.schedule.assignment.dto.UserAssignmentsBatchMessage.AssignmentPayload;
import com.unisync.schedule.categories.service.CategoryService;
import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.common.entity.Schedule.ScheduleSource;
import com.unisync.schedule.common.entity.Schedule.ScheduleStatus;
import com.unisync.schedule.common.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * AssignmentService (Schedule-Service) 단위 테스트
 * Assignment → Schedule 변환 로직 검증
 */
@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private AssignmentService assignmentService;

    @Captor
    private ArgumentCaptor<Schedule> scheduleCaptor;

    private UserAssignmentsBatchMessage validMessage;
    private Long canvasCategoryId;

    @BeforeEach
    void setUp() {
        canvasCategoryId = 100L;

        validMessage = UserAssignmentsBatchMessage.builder()
            .eventType("USER_ASSIGNMENTS_CREATED")
            .cognitoSub("user-123")
            .assignments(Arrays.asList(
                AssignmentPayload.builder()
                    .assignmentId(1L)
                    .canvasAssignmentId(456L)
                    .canvasCourseId(789L)
                    .courseId(10L)
                    .courseName("데이터구조")
                    .title("중간고사 프로젝트")
                    .description("Spring Boot로 REST API 구현")
                    .dueAt("2025-11-15T23:59:59")
                    .pointsPossible(100.0)
                    .build(),
                AssignmentPayload.builder()
                    .assignmentId(2L)
                    .canvasAssignmentId(457L)
                    .canvasCourseId(790L)
                    .courseId(11L)
                    .courseName("알고리즘")
                    .title("기말 프로젝트")
                    .description("정렬 알고리즘 구현")
                    .dueAt("2025-12-20T23:59:59")
                    .pointsPossible(120.0)
                    .build()
            ))
            .build();
    }

    @Test
    @DisplayName("배치 이벤트로 일정 생성 및 불포함 일정 삭제")
    void processAssignmentsBatch_createsAndPrunesSchedules() {
        // given: 기존 일정 하나(불포함) + 배치 내 두 개 생성
        Schedule existingStale = Schedule.builder()
            .scheduleId(99L)
            .cognitoSub("user-123")
            .categoryId(50L)
            .title("old")
            .source(ScheduleSource.CANVAS)
            .sourceId("canvas-assignment-999-user-123")
            .build();

        given(scheduleRepository.findByCognitoSubAndSource("user-123", ScheduleSource.CANVAS))
            .willReturn(List.of(existingStale));

        given(categoryService.getOrCreateCourseCategory(eq("user-123"), any(), any()))
            .willReturn(canvasCategoryId);

        given(scheduleRepository.save(any(Schedule.class)))
            .willAnswer(invocation -> {
                Schedule arg = invocation.getArgument(0);
                arg.setScheduleId(arg.getScheduleId() == null ? 1L : arg.getScheduleId());
                return arg;
            });

        // when
        assignmentService.processAssignmentsBatch(validMessage);

        // then
        then(scheduleRepository).should(times(2)).save(scheduleCaptor.capture());
        then(scheduleRepository).should(times(1)).delete(existingStale);

        List<Schedule> saved = scheduleCaptor.getAllValues();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getSource()).isEqualTo(ScheduleSource.CANVAS);
        assertThat(saved.get(0).getIsAllDay()).isTrue();
        assertThat(saved.get(0).getTitle()).startsWith("[데이터구조]");
        assertThat(saved.get(0).getStartTime()).isEqualTo(LocalDateTime.of(2025, 11, 15, 0, 0, 0));
    }

    @Test
    @DisplayName("dueAt이 null인 과제는 기존 일정 삭제 후 건너뜀")
    void processAssignmentsBatch_dueAtNull_deletesExisting() {
        AssignmentPayload withoutDue = AssignmentPayload.builder()
            .assignmentId(3L)
            .canvasAssignmentId(999L)
            .canvasCourseId(700L)
            .courseId(12L)
            .courseName("네트워크")
            .title("보고서")
            .description("null dueAt")
            .dueAt(null)
            .build();

        validMessage.setAssignments(List.of(withoutDue));

        Schedule existing = Schedule.builder()
            .scheduleId(5L)
            .cognitoSub("user-123")
            .source(ScheduleSource.CANVAS)
            .sourceId("canvas-assignment-999-user-123")
            .build();

        given(scheduleRepository.findByCognitoSubAndSource("user-123", ScheduleSource.CANVAS))
            .willReturn(List.of(existing));

        // when
        assignmentService.processAssignmentsBatch(validMessage);

        // then
        then(scheduleRepository).should(times(1)).delete(existing);
        then(scheduleRepository).should(never()).save(any(Schedule.class));
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입은 무시")
    void processAssignmentsBatch_ignoresUnknownEvent() {
        validMessage.setEventType("UNKNOWN");

        assignmentService.processAssignmentsBatch(validMessage);

        then(scheduleRepository).should(never()).save(any(Schedule.class));
        then(scheduleRepository).should(never()).delete(any(Schedule.class));
    }
}
