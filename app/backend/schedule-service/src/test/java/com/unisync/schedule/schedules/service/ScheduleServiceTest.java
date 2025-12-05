package com.unisync.schedule.schedules.service;

import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.common.repository.ScheduleRepository;
import com.unisync.schedule.internal.client.UserServiceClient;
import com.unisync.schedule.internal.service.GroupPermissionService;
import com.unisync.schedule.schedules.dto.ScheduleResponse;
import com.unisync.schedule.schedules.exception.ScheduleNotFoundException;
import com.unisync.schedule.todos.dto.TodoWithSubtasksResponse;
import com.unisync.schedule.todos.service.TodoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private GroupPermissionService groupPermissionService;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private TodoService todoService;

    @InjectMocks
    private ScheduleService scheduleService;

    @Test
    void test_getScheduleById_personalSchedule_returnsTodos() {
        Schedule schedule = personalSchedule();
        List<TodoWithSubtasksResponse> todos = List.of(TodoWithSubtasksResponse.builder().todoId(1L).build());

        given(scheduleRepository.findById(5L)).willReturn(Optional.of(schedule));
        given(todoService.getTodosByScheduleIdWithSubtasks(5L)).willReturn(todos);

        ScheduleResponse response = scheduleService.getScheduleById(5L, "user-123");

        assertThat(response.getScheduleId()).isEqualTo(5L);
        assertThat(response.getTodos()).hasSize(1);
        verify(groupPermissionService, never()).validateReadPermission(anyLong(), anyString());
    }

    @Test
    void test_getScheduleById_groupSchedule_validatesPermission() {
        Schedule schedule = groupSchedule();

        given(scheduleRepository.findById(7L)).willReturn(Optional.of(schedule));
        given(todoService.getTodosByScheduleIdWithSubtasks(7L)).willReturn(List.of());

        scheduleService.getScheduleById(7L, "member-1");

        verify(groupPermissionService).validateReadPermission(20L, "member-1");
    }

    @Test
    void test_getScheduleById_notFound_throwsException() {
        given(scheduleRepository.findById(anyLong())).willReturn(Optional.empty());

        assertThrows(ScheduleNotFoundException.class, () -> scheduleService.getScheduleById(999L, "user-123"));
    }

    private Schedule personalSchedule() {
        return Schedule.builder()
                .scheduleId(5L)
                .cognitoSub("user-123")
                .categoryId(1L)
                .title("Midterm")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusHours(2))
                .build();
    }

    private Schedule groupSchedule() {
        return Schedule.builder()
                .scheduleId(7L)
                .cognitoSub("owner")
                .groupId(20L)
                .categoryId(1L)
                .title("Group Meeting")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusHours(1))
                .build();
    }
}
