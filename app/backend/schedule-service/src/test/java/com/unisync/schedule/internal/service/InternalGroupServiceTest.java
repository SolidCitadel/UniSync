package com.unisync.schedule.internal.service;

import com.unisync.schedule.common.entity.Category;
import com.unisync.schedule.common.entity.Schedule;
import com.unisync.schedule.common.entity.Todo;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.common.repository.ScheduleRepository;
import com.unisync.schedule.common.repository.TodoRepository;
import com.unisync.schedule.internal.dto.GroupDataDeleteResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalGroupService 단위 테스트")
class InternalGroupServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private TodoRepository todoRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private InternalGroupService internalGroupService;

    @Test
    @DisplayName("그룹 데이터 삭제 - 데이터가 있는 경우 모두 삭제")
    void test_deleteGroupData_WithData_ShouldDeleteAll() {
        // given
        Long groupId = 1L;

        Schedule schedule = Schedule.builder().scheduleId(1L).groupId(groupId).build();
        Todo todo = Todo.builder().todoId(1L).groupId(groupId).build();
        Category category = Category.builder().categoryId(1L).groupId(groupId).build();

        given(scheduleRepository.existsByGroupId(groupId)).willReturn(true);
        given(todoRepository.existsByGroupId(groupId)).willReturn(true);
        given(categoryRepository.existsByGroupId(groupId)).willReturn(true);
        given(scheduleRepository.findByGroupId(groupId)).willReturn(List.of(schedule));
        given(todoRepository.findByGroupId(groupId)).willReturn(List.of(todo));
        given(categoryRepository.findByGroupId(groupId)).willReturn(List.of(category));

        // when
        GroupDataDeleteResponse response = internalGroupService.deleteGroupData(groupId);

        // then
        assertThat(response.getGroupId()).isEqualTo(groupId);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getDeletedSchedules()).isEqualTo(1);
        assertThat(response.getDeletedTodos()).isEqualTo(1);
        assertThat(response.getDeletedCategories()).isEqualTo(1);

        then(todoRepository).should(times(1)).deleteByGroupId(groupId);
        then(scheduleRepository).should(times(1)).deleteByGroupId(groupId);
        then(categoryRepository).should(times(1)).deleteByGroupId(groupId);
    }

    @Test
    @DisplayName("그룹 데이터 삭제 - 데이터가 없는 경우")
    void test_deleteGroupData_NoData_ShouldReturnNoDataResponse() {
        // given
        Long groupId = 999L;

        given(scheduleRepository.existsByGroupId(groupId)).willReturn(false);
        given(todoRepository.existsByGroupId(groupId)).willReturn(false);
        given(categoryRepository.existsByGroupId(groupId)).willReturn(false);

        // when
        GroupDataDeleteResponse response = internalGroupService.deleteGroupData(groupId);

        // then
        assertThat(response.getGroupId()).isEqualTo(groupId);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getDeletedSchedules()).isEqualTo(0);
        assertThat(response.getDeletedTodos()).isEqualTo(0);
        assertThat(response.getDeletedCategories()).isEqualTo(0);

        then(todoRepository).should(never()).deleteByGroupId(any());
        then(scheduleRepository).should(never()).deleteByGroupId(any());
        then(categoryRepository).should(never()).deleteByGroupId(any());
    }

    @Test
    @DisplayName("그룹 데이터 삭제 - 일정만 있는 경우")
    void test_deleteGroupData_OnlySchedules_ShouldDeleteOnlySchedules() {
        // given
        Long groupId = 1L;

        Schedule schedule1 = Schedule.builder().scheduleId(1L).groupId(groupId).build();
        Schedule schedule2 = Schedule.builder().scheduleId(2L).groupId(groupId).build();

        given(scheduleRepository.existsByGroupId(groupId)).willReturn(true);
        given(todoRepository.existsByGroupId(groupId)).willReturn(false);
        given(categoryRepository.existsByGroupId(groupId)).willReturn(false);
        given(scheduleRepository.findByGroupId(groupId)).willReturn(List.of(schedule1, schedule2));

        // when
        GroupDataDeleteResponse response = internalGroupService.deleteGroupData(groupId);

        // then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getDeletedSchedules()).isEqualTo(2);
        assertThat(response.getDeletedTodos()).isEqualTo(0);
        assertThat(response.getDeletedCategories()).isEqualTo(0);

        then(scheduleRepository).should(times(1)).deleteByGroupId(groupId);
        then(todoRepository).should(never()).deleteByGroupId(any());
        then(categoryRepository).should(never()).deleteByGroupId(any());
    }
}
