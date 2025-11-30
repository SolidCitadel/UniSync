package com.unisync.schedule.assignment.service;

import com.unisync.schedule.assignment.dto.AssignmentToScheduleMessage;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private AssignmentToScheduleMessage validMessage;
    private Long canvasCategoryId;

    @BeforeEach
    void setUp() {
        canvasCategoryId = 100L;

        validMessage = AssignmentToScheduleMessage.builder()
            .eventType("ASSIGNMENT_CREATED")
            .assignmentId(1L)
            .cognitoSub("user-123")
            .canvasAssignmentId(456L)
            .canvasCourseId(789L)
            .title("중간고사 프로젝트")
            .description("Spring Boot로 REST API 구현")
            .dueAt(LocalDateTime.of(2025, 11, 15, 23, 59, 59))
            .pointsPossible(100)
            .courseId(10L)
            .courseName("데이터구조")
            .build();
    }

    @Test
    @DisplayName("ASSIGNMENT_CREATED 이벤트 처리 성공")
    void processAssignmentEvent_Created_Success() {
        // given
        given(scheduleRepository.existsBySourceAndSourceId(any(), anyString()))
            .willReturn(false);
        given(categoryService.getOrCreateCanvasCategory(validMessage.getCognitoSub()))
            .willReturn(canvasCategoryId);
        given(scheduleRepository.save(any(Schedule.class)))
            .willAnswer(invocation -> {
                Schedule arg = invocation.getArgument(0);
                arg.setScheduleId(1L);
                return arg;
            });

        // when
        assignmentService.processAssignmentEvent(validMessage);

        // then
        then(scheduleRepository).should(times(1)).existsBySourceAndSourceId(
            eq(ScheduleSource.CANVAS),
            eq("canvas-assignment-456-user-123")
        );
        then(categoryService).should(times(1)).getOrCreateCanvasCategory("user-123");
        then(scheduleRepository).should(times(1)).save(scheduleCaptor.capture());

        Schedule savedSchedule = scheduleCaptor.getValue();
        assertThat(savedSchedule.getCognitoSub()).isEqualTo("user-123");
        assertThat(savedSchedule.getGroupId()).isNull();
        assertThat(savedSchedule.getCategoryId()).isEqualTo(canvasCategoryId);
        assertThat(savedSchedule.getTitle()).isEqualTo("[데이터구조] 중간고사 프로젝트");
        assertThat(savedSchedule.getDescription()).isEqualTo("Spring Boot로 REST API 구현");

        LocalDateTime expectedTime = validMessage.getDueAt().toLocalDate().atStartOfDay();
        assertThat(savedSchedule.getStartTime()).isEqualTo(expectedTime);
        assertThat(savedSchedule.getEndTime()).isEqualTo(expectedTime);  // start와 end가 동일
        assertThat(savedSchedule.getIsAllDay()).isTrue();  // Canvas 과제는 하루 종일 이벤트
        assertThat(savedSchedule.getStatus()).isEqualTo(ScheduleStatus.TODO);
        assertThat(savedSchedule.getSource()).isEqualTo(ScheduleSource.CANVAS);
        assertThat(savedSchedule.getSourceId()).isEqualTo("canvas-assignment-456-user-123");
    }

    @Test
    @DisplayName("중복된 Schedule은 생성하지 않음")
    void processAssignmentEvent_Created_AlreadyExists() {
        // given
        given(scheduleRepository.existsBySourceAndSourceId(any(), anyString()))
            .willReturn(true);

        // when
        assignmentService.processAssignmentEvent(validMessage);

        // then
        then(scheduleRepository).should(times(1)).existsBySourceAndSourceId(any(), anyString());
        then(categoryService).should(never()).getOrCreateCanvasCategory(anyString());
        then(scheduleRepository).should(never()).save(any(Schedule.class));
    }

    @Test
    @DisplayName("ASSIGNMENT_UPDATED 이벤트 처리 성공")
    void processAssignmentEvent_Updated_Success() {
        // given
        validMessage.setEventType("ASSIGNMENT_UPDATED");
        validMessage.setTitle("업데이트된 제목");

        Schedule existingSchedule = Schedule.builder()
            .scheduleId(1L)
            .cognitoSub("user-123")
            .categoryId(canvasCategoryId)
            .title("[데이터구조] 기존 제목")
            .description("기존 설명")
            .startTime(LocalDateTime.of(2025, 11, 10, 23, 59, 59))
            .endTime(LocalDateTime.of(2025, 11, 11, 23, 59, 59))
            .isAllDay(false)
            .status(ScheduleStatus.TODO)
            .source(ScheduleSource.CANVAS)
            .sourceId("canvas-assignment-456-user-123")
            .build();

        given(scheduleRepository.findBySourceAndSourceId(any(), anyString()))
            .willReturn(Optional.of(existingSchedule));
        given(scheduleRepository.save(any(Schedule.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        assignmentService.processAssignmentEvent(validMessage);

        // then
        then(scheduleRepository).should(times(1)).findBySourceAndSourceId(any(), anyString());
        then(scheduleRepository).should(times(1)).save(scheduleCaptor.capture());

        Schedule updatedSchedule = scheduleCaptor.getValue();
        assertThat(updatedSchedule.getTitle()).isEqualTo("[데이터구조] 업데이트된 제목");
        assertThat(updatedSchedule.getDescription()).isEqualTo(validMessage.getDescription());

        LocalDateTime expectedTime = validMessage.getDueAt().toLocalDate().atStartOfDay();
        assertThat(updatedSchedule.getStartTime()).isEqualTo(expectedTime);
        assertThat(updatedSchedule.getEndTime()).isEqualTo(expectedTime);  // start와 end가 동일
    }

    @Test
    @DisplayName("ASSIGNMENT_UPDATED 시 Schedule이 없으면 예외 발생")
    void processAssignmentEvent_Updated_NotFound() {
        // given
        validMessage.setEventType("ASSIGNMENT_UPDATED");

        given(scheduleRepository.findBySourceAndSourceId(any(), anyString()))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> assignmentService.processAssignmentEvent(validMessage))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Schedule not found");

        then(scheduleRepository).should(never()).save(any(Schedule.class));
    }

    @Test
    @DisplayName("ASSIGNMENT_DELETED 이벤트 처리 성공")
    void processAssignmentEvent_Deleted_Success() {
        // given
        validMessage.setEventType("ASSIGNMENT_DELETED");

        Schedule existingSchedule = Schedule.builder()
            .scheduleId(1L)
            .cognitoSub("user-123")
            .categoryId(canvasCategoryId)
            .title("[데이터구조] 중간고사 프로젝트")
            .source(ScheduleSource.CANVAS)
            .sourceId("canvas-assignment-456-user-123")
            .build();

        given(scheduleRepository.findBySourceAndSourceId(any(), anyString()))
            .willReturn(Optional.of(existingSchedule));

        // when
        assignmentService.processAssignmentEvent(validMessage);

        // then
        then(scheduleRepository).should(times(1)).findBySourceAndSourceId(any(), anyString());
        then(scheduleRepository).should(times(1)).delete(existingSchedule);
    }

    @Test
    @DisplayName("ASSIGNMENT_DELETED 시 Schedule이 없어도 예외 발생하지 않음")
    void processAssignmentEvent_Deleted_NotFound() {
        // given
        validMessage.setEventType("ASSIGNMENT_DELETED");

        given(scheduleRepository.findBySourceAndSourceId(any(), anyString()))
            .willReturn(Optional.empty());

        // when
        assignmentService.processAssignmentEvent(validMessage);

        // then
        then(scheduleRepository).should(times(1)).findBySourceAndSourceId(any(), anyString());
        then(scheduleRepository).should(never()).delete(any(Schedule.class));
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입은 무시")
    void processAssignmentEvent_UnknownEventType() {
        // given
        validMessage.setEventType("UNKNOWN_EVENT");

        // when
        assignmentService.processAssignmentEvent(validMessage);

        // then
        then(scheduleRepository).should(never()).save(any(Schedule.class));
        then(scheduleRepository).should(never()).delete(any(Schedule.class));
    }

    @Test
    @DisplayName("Schedule 제목은 [과목명] 과제명 형식으로 생성")
    void createSchedule_TitleFormat() {
        // given
        given(scheduleRepository.existsBySourceAndSourceId(any(), anyString()))
            .willReturn(false);
        given(categoryService.getOrCreateCanvasCategory(validMessage.getCognitoSub()))
            .willReturn(canvasCategoryId);
        given(scheduleRepository.save(any(Schedule.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        assignmentService.processAssignmentEvent(validMessage);

        // then
        then(scheduleRepository).should(times(1)).save(scheduleCaptor.capture());

        Schedule savedSchedule = scheduleCaptor.getValue();
        assertThat(savedSchedule.getTitle()).startsWith("[" + validMessage.getCourseName() + "]");
        assertThat(savedSchedule.getTitle()).contains(validMessage.getTitle());
    }

    @Test
    @DisplayName("하루 종일 이벤트로 시작 시간은 해당 날짜 00:00:00으로 설정")
    void createSchedule_AllDayEvent_StartTimeAtMidnight() {
        // given
        LocalDateTime dueAt = LocalDateTime.of(2025, 11, 20, 23, 59, 59);
        validMessage.setDueAt(dueAt);

        given(scheduleRepository.existsBySourceAndSourceId(any(), anyString()))
            .willReturn(false);
        given(categoryService.getOrCreateCanvasCategory(validMessage.getCognitoSub()))
            .willReturn(canvasCategoryId);
        given(scheduleRepository.save(any(Schedule.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        assignmentService.processAssignmentEvent(validMessage);

        // then
        then(scheduleRepository).should(times(1)).save(scheduleCaptor.capture());

        Schedule savedSchedule = scheduleCaptor.getValue();
        LocalDateTime expectedTime = LocalDateTime.of(2025, 11, 20, 0, 0, 0);
        assertThat(savedSchedule.getStartTime()).isEqualTo(expectedTime);
        assertThat(savedSchedule.getEndTime()).isEqualTo(expectedTime);  // start와 end가 동일
        assertThat(savedSchedule.getIsAllDay()).isTrue();
    }

    @Test
    @DisplayName("dueAt이 null인 경우 일정 생성을 건너뜀")
    void test_createSchedule_dueAtNull_skipsCreation() {
        // given
        validMessage.setDueAt(null);

        // when
        assignmentService.processAssignmentEvent(validMessage);

        // then
        then(scheduleRepository).should(never()).existsBySourceAndSourceId(any(), anyString());
        then(categoryService).should(never()).getOrCreateCanvasCategory(anyString());
        then(scheduleRepository).should(never()).save(any(Schedule.class));
    }

    @Test
    @DisplayName("ASSIGNMENT_UPDATED 시 dueAt이 null이면 일정 삭제")
    void test_updateSchedule_dueAtNull_deletesSchedule() {
        // given
        validMessage.setEventType("ASSIGNMENT_UPDATED");
        validMessage.setDueAt(null);

        Schedule existingSchedule = Schedule.builder()
            .scheduleId(1L)
            .cognitoSub("user-123")
            .categoryId(canvasCategoryId)
            .title("[데이터구조] 기존 제목")
            .description("기존 설명")
            .startTime(LocalDateTime.of(2025, 11, 10, 23, 59, 59))
            .endTime(LocalDateTime.of(2025, 11, 11, 23, 59, 59))
            .isAllDay(false)
            .status(ScheduleStatus.TODO)
            .source(ScheduleSource.CANVAS)
            .sourceId("canvas-assignment-456-user-123")
            .build();

        given(scheduleRepository.findBySourceAndSourceId(any(), anyString()))
            .willReturn(Optional.of(existingSchedule));

        // when
        assignmentService.processAssignmentEvent(validMessage);

        // then
        then(scheduleRepository).should(times(1)).findBySourceAndSourceId(
            eq(ScheduleSource.CANVAS),
            eq("canvas-assignment-456-user-123")
        );
        then(scheduleRepository).should(times(1)).delete(existingSchedule);
        then(scheduleRepository).should(never()).save(any(Schedule.class));
    }

    @Test
    @DisplayName("ASSIGNMENT_UPDATED 시 dueAt이 null이고 일정이 없어도 예외 발생하지 않음")
    void test_updateSchedule_dueAtNull_scheduleNotFound_noException() {
        // given
        validMessage.setEventType("ASSIGNMENT_UPDATED");
        validMessage.setDueAt(null);

        given(scheduleRepository.findBySourceAndSourceId(any(), anyString()))
            .willReturn(Optional.empty());

        // when & then (예외 발생하지 않아야 함)
        assignmentService.processAssignmentEvent(validMessage);

        then(scheduleRepository).should(times(1)).findBySourceAndSourceId(any(), anyString());
        then(scheduleRepository).should(never()).delete(any(Schedule.class));
        then(scheduleRepository).should(never()).save(any(Schedule.class));
    }
}
