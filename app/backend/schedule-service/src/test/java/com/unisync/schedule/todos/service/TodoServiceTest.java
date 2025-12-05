package com.unisync.schedule.todos.service;

import com.unisync.schedule.categories.exception.CategoryNotFoundException;
import com.unisync.schedule.common.entity.Category;
import com.unisync.schedule.common.entity.Todo;
import com.unisync.schedule.common.entity.Todo.TodoPriority;
import com.unisync.schedule.common.entity.Todo.TodoStatus;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.common.repository.TodoRepository;
import com.unisync.schedule.internal.client.UserServiceClient;
import com.unisync.schedule.internal.service.GroupPermissionService;
import com.unisync.schedule.todos.dto.TodoRequest;
import com.unisync.schedule.todos.dto.TodoResponse;
import com.unisync.schedule.todos.dto.TodoWithSubtasksResponse;
import com.unisync.schedule.todos.exception.InvalidTodoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private GroupPermissionService groupPermissionService;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private TodoService todoService;

    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .categoryId(1L)
                .cognitoSub("user-123")
                .name("Default")
                .color("#FFFFFF")
                .isDefault(false)
                .build();

        given(categoryRepository.findById(anyLong())).willReturn(Optional.of(category));
    }

    @Test
    void test_createTodo_withDeadlineBeforeDueDate_throwsInvalidTodoException() {
        TodoRequest request = TodoRequest.builder()
                .title("Test Todo")
                .startDate(LocalDate.of(2025, 1, 1))
                .dueDate(LocalDate.of(2025, 1, 2))
                .deadline(LocalDateTime.of(2025, 1, 1, 23, 0))
                .categoryId(1L)
                .priority(TodoPriority.MEDIUM)
                .build();

        assertThrows(InvalidTodoException.class, () -> todoService.createTodo(request, "user-123"));

        verify(todoRepository, never()).save(any());
    }

    @Test
    void test_getTodos_includeGroupsTrue_returnsPersonalAndGroupTodos() {
        List<Long> groupIds = List.of(10L, 11L);
        Todo personal = sampleTodo(1L, "user-123", null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2), TodoStatus.TODO, TodoPriority.MEDIUM);
        Todo group = sampleTodo(2L, "user-123", 10L, LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 4), TodoStatus.DONE, TodoPriority.HIGH);

        given(userServiceClient.getUserGroupIds("user-123")).willReturn(groupIds);
        given(todoRepository.findByCognitoSubOrGroupIdIn("user-123", groupIds)).willReturn(List.of(personal, group));

        List<TodoResponse> responses = todoService.getTodos("user-123", null, true, null, null, null, null);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(TodoResponse::getTodoId).containsExactlyInAnyOrder(1L, 2L);
        verify(todoRepository, never()).findByGroupId(anyLong());
    }

    @Test
    void test_getTodos_withInvalidStatus_throwsInvalidTodoException() {
        Todo personal = sampleTodo(1L, "user-123", null, LocalDate.now(), LocalDate.now(), TodoStatus.TODO, TodoPriority.MEDIUM);
        given(todoRepository.findByCognitoSub("user-123")).willReturn(List.of(personal));

        assertThrows(InvalidTodoException.class,
                () -> todoService.getTodos("user-123", null, false, null, null, "INVALID_STATUS", null));
    }

    @Test
    void test_getTodosByScheduleIdWithSubtasks_returnsNestedStructure() {
        Todo root = sampleTodo(1L, "user-123", null, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 2), TodoStatus.TODO, TodoPriority.MEDIUM);
        root.setDeadline(LocalDateTime.of(2025, 2, 2, 23, 0));
        Todo child = sampleTodo(2L, "user-123", null, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 1), TodoStatus.IN_PROGRESS, TodoPriority.LOW);
        child.setParentTodoId(1L);

        given(todoRepository.findByScheduleIdAndParentTodoIdIsNull(100L)).willReturn(List.of(root));
        given(todoRepository.findByParentTodoId(1L)).willReturn(List.of(child));
        given(todoRepository.findByParentTodoId(2L)).willReturn(List.of());

        List<TodoWithSubtasksResponse> responses = todoService.getTodosByScheduleIdWithSubtasks(100L);

        assertThat(responses).hasSize(1);
        TodoWithSubtasksResponse rootResponse = responses.get(0);
        assertThat(rootResponse.getTodoId()).isEqualTo(1L);
        assertThat(rootResponse.getDeadline()).isEqualTo(root.getDeadline());
        assertThat(rootResponse.getSubtasks()).hasSize(1);
        assertThat(rootResponse.getSubtasks().get(0).getTodoId()).isEqualTo(2L);
    }

    @Test
    void test_createTodo_withMissingCategory_throwsCategoryNotFound() {
        given(categoryRepository.findById(anyLong())).willReturn(Optional.empty());

        TodoRequest request = TodoRequest.builder()
                .title("Test")
                .startDate(LocalDate.now())
                .dueDate(LocalDate.now())
                .categoryId(99L)
                .priority(TodoPriority.MEDIUM)
                .build();

        assertThrows(CategoryNotFoundException.class, () -> todoService.createTodo(request, "user-123"));
    }

    private Todo sampleTodo(Long id, String cognitoSub, Long groupId, LocalDate start, LocalDate due, TodoStatus status, TodoPriority priority) {
        return Todo.builder()
                .todoId(id)
                .cognitoSub(cognitoSub)
                .groupId(groupId)
                .categoryId(1L)
                .title("todo-" + id)
                .description("desc")
                .startDate(start)
                .dueDate(due)
                .status(status)
                .priority(priority)
                .progressPercentage(0)
                .isAiGenerated(false)
                .build();
    }
}
