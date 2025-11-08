package com.unisync.schedule.todos.service;

import com.unisync.schedule.categories.exception.CategoryNotFoundException;
import com.unisync.schedule.common.entity.Category;
import com.unisync.schedule.common.entity.Todo;
import com.unisync.schedule.common.entity.Todo.TodoPriority;
import com.unisync.schedule.common.entity.Todo.TodoStatus;
import com.unisync.schedule.common.exception.UnauthorizedAccessException;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.common.repository.TodoRepository;
import com.unisync.schedule.todos.dto.TodoRequest;
import com.unisync.schedule.todos.dto.TodoResponse;
import com.unisync.schedule.todos.exception.InvalidTodoException;
import com.unisync.schedule.todos.exception.TodoNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TodoService 단위 테스트")
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private TodoService todoService;

    private Todo testTodo;
    private Todo parentTodo;
    private Todo subtask1;
    private Todo subtask2;
    private TodoRequest testRequest;
    private Long userId;
    private Long todoId;
    private Long parentTodoId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        userId = 1L;
        todoId = 100L;
        parentTodoId = 200L;
        categoryId = 10L;

        testRequest = new TodoRequest();
        testRequest.setCategoryId(categoryId);
        testRequest.setTitle("프로젝트 완성하기");
        testRequest.setDescription("Spring Boot 프로젝트");
        testRequest.setStartDate(LocalDate.of(2025, 11, 1));
        testRequest.setDueDate(LocalDate.of(2025, 11, 15));
        testRequest.setPriority(TodoPriority.HIGH);

        testTodo = new Todo();
        testTodo.setTodoId(todoId);
        testTodo.setUserId(userId);
        testTodo.setCategoryId(categoryId);
        testTodo.setTitle("프로젝트 완성하기");
        testTodo.setDescription("Spring Boot 프로젝트");
        testTodo.setStartDate(LocalDate.of(2025, 11, 1));
        testTodo.setDueDate(LocalDate.of(2025, 11, 15));
        testTodo.setStatus(TodoStatus.TODO);
        testTodo.setPriority(TodoPriority.HIGH);
        testTodo.setProgressPercentage(0);
        testTodo.setIsAiGenerated(false);

        parentTodo = new Todo();
        parentTodo.setTodoId(parentTodoId);
        parentTodo.setUserId(userId);
        parentTodo.setCategoryId(categoryId);
        parentTodo.setTitle("부모 할일");
        parentTodo.setStartDate(LocalDate.of(2025, 11, 1));
        parentTodo.setDueDate(LocalDate.of(2025, 11, 30));
        parentTodo.setStatus(TodoStatus.TODO);
        parentTodo.setProgressPercentage(0);

        subtask1 = new Todo();
        subtask1.setTodoId(101L);
        subtask1.setUserId(userId);
        subtask1.setParentTodoId(parentTodoId);
        subtask1.setTitle("서브태스크 1");
        subtask1.setStartDate(LocalDate.of(2025, 11, 1));
        subtask1.setDueDate(LocalDate.of(2025, 11, 15));
        subtask1.setProgressPercentage(50);
        subtask1.setStatus(TodoStatus.IN_PROGRESS);

        subtask2 = new Todo();
        subtask2.setTodoId(102L);
        subtask2.setUserId(userId);
        subtask2.setParentTodoId(parentTodoId);
        subtask2.setTitle("서브태스크 2");
        subtask2.setStartDate(LocalDate.of(2025, 11, 16));
        subtask2.setDueDate(LocalDate.of(2025, 11, 30));
        subtask2.setProgressPercentage(100);
        subtask2.setStatus(TodoStatus.DONE);
    }

    @Test
    @DisplayName("할일 생성 성공")
    void createTodo_Success() {
        // given
        Category category = new Category();
        category.setCategoryId(categoryId);
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(category));
        given(todoRepository.save(any(Todo.class))).willReturn(testTodo);

        // when
        TodoResponse response = todoService.createTodo(testRequest, userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getTodoId()).isEqualTo(todoId);
        assertThat(response.getTitle()).isEqualTo("프로젝트 완성하기");

        then(categoryRepository).should().findById(categoryId);
        then(todoRepository).should().save(any(Todo.class));
    }

    @Test
    @DisplayName("할일 생성 실패 - 마감일이 시작일보다 이전")
    void createTodo_InvalidDateRange() {
        // given
        testRequest.setStartDate(LocalDate.of(2025, 11, 15));
        testRequest.setDueDate(LocalDate.of(2025, 11, 1));

        // when & then
        assertThatThrownBy(() -> todoService.createTodo(testRequest, userId))
                .isInstanceOf(InvalidTodoException.class)
                .hasMessageContaining("마감 날짜는 시작 날짜보다 늦어야 합니다");

        then(todoRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("할일 생성 실패 - 존재하지 않는 카테고리")
    void createTodo_CategoryNotFound() {
        // given
        given(categoryRepository.findById(categoryId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> todoService.createTodo(testRequest, userId))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("카테고리를 찾을 수 없습니다");

        then(todoRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("서브태스크 생성 성공")
    void createSubtask_Success() {
        // given
        Category category = new Category();
        category.setCategoryId(categoryId);
        testRequest.setParentTodoId(parentTodoId);
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(category));
        given(todoRepository.findById(parentTodoId)).willReturn(Optional.of(parentTodo));
        given(todoRepository.save(any(Todo.class))).willReturn(testTodo);

        // when
        TodoResponse response = todoService.createTodo(testRequest, userId);

        // then
        assertThat(response).isNotNull();
        then(todoRepository).should().findById(parentTodoId);
        then(todoRepository).should().save(any(Todo.class));
    }

    @Test
    @DisplayName("서브태스크 생성 실패 - 부모 할일 없음")
    void createSubtask_ParentNotFound() {
        // given
        Category category = new Category();
        category.setCategoryId(categoryId);
        testRequest.setParentTodoId(parentTodoId);
        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(category));
        given(todoRepository.findById(parentTodoId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> todoService.createTodo(testRequest, userId))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("부모 할일을 찾을 수 없습니다");

        then(todoRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("할일 ID로 조회 성공")
    void getTodoById_Success() {
        // given
        given(todoRepository.findById(todoId)).willReturn(Optional.of(testTodo));

        // when
        TodoResponse response = todoService.getTodoById(todoId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getTodoId()).isEqualTo(todoId);
        assertThat(response.getTitle()).isEqualTo("프로젝트 완성하기");

        then(todoRepository).should().findById(todoId);
    }

    @Test
    @DisplayName("할일 ID로 조회 실패 - 존재하지 않는 할일")
    void getTodoById_NotFound() {
        // given
        given(todoRepository.findById(todoId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> todoService.getTodoById(todoId))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("할일을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("사용자 ID로 할일 목록 조회 성공")
    void getTodosByUserId_Success() {
        // given
        List<Todo> todos = List.of(testTodo);
        given(todoRepository.findByUserId(userId)).willReturn(todos);

        // when
        List<TodoResponse> responses = todoService.getTodosByUserId(userId);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getTodoId()).isEqualTo(todoId);
        assertThat(responses.get(0).getUserId()).isEqualTo(userId);

        then(todoRepository).should().findByUserId(userId);
    }

    @Test
    @DisplayName("서브태스크 목록 조회 성공")
    void getSubtasks_Success() {
        // given
        given(todoRepository.findById(parentTodoId)).willReturn(Optional.of(parentTodo)); // 부모 할일 존재 확인
        List<Todo> subtasks = List.of(subtask1, subtask2);
        given(todoRepository.findByParentTodoId(parentTodoId)).willReturn(subtasks);

        // when
        List<TodoResponse> responses = todoService.getSubtasks(parentTodoId);

        // then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getParentTodoId()).isEqualTo(parentTodoId);
        assertThat(responses.get(1).getParentTodoId()).isEqualTo(parentTodoId);

        then(todoRepository).should().findById(parentTodoId);
        then(todoRepository).should().findByParentTodoId(parentTodoId);
    }

    @Test
    @DisplayName("할일 수정 성공")
    void updateTodo_Success() {
        // given
        given(todoRepository.findById(todoId)).willReturn(Optional.of(testTodo));
        given(todoRepository.save(any(Todo.class))).willReturn(testTodo);

        TodoRequest updateRequest = new TodoRequest();
        updateRequest.setCategoryId(categoryId); // 같은 카테고리 ID 사용 (변경 없음)
        updateRequest.setTitle("수정된 제목");
        updateRequest.setDescription("수정된 설명");
        updateRequest.setStartDate(LocalDate.of(2025, 11, 5));
        updateRequest.setDueDate(LocalDate.of(2025, 11, 20));
        updateRequest.setPriority(TodoPriority.URGENT);

        // when
        TodoResponse response = todoService.updateTodo(todoId, updateRequest, userId);

        // then
        assertThat(response).isNotNull();
        then(todoRepository).should().findById(todoId);
        then(todoRepository).should().save(any(Todo.class));
    }

    @Test
    @DisplayName("할일 수정 실패 - 권한 없음")
    void updateTodo_Unauthorized() {
        // given
        Long unauthorizedUserId = 999L;
        given(todoRepository.findById(todoId)).willReturn(Optional.of(testTodo));

        // when & then
        assertThatThrownBy(() -> todoService.updateTodo(todoId, testRequest, unauthorizedUserId))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("해당 할일에 접근할 권한이 없습니다");

        then(todoRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("할일 상태 변경 성공")
    void updateTodoStatus_Success() {
        // given
        given(todoRepository.findById(todoId)).willReturn(Optional.of(testTodo));
        given(todoRepository.save(any(Todo.class))).willReturn(testTodo);

        // when
        TodoResponse response = todoService.updateTodoStatus(todoId, TodoStatus.DONE, userId);

        // then
        assertThat(response).isNotNull();
        then(todoRepository).should().findById(todoId);
        then(todoRepository).should().save(any(Todo.class));
    }

    @Test
    @DisplayName("할일 진행률 변경 성공")
    void updateTodoProgress_Success() {
        // given
        given(todoRepository.findById(todoId)).willReturn(Optional.of(testTodo));
        given(todoRepository.save(any(Todo.class))).willReturn(testTodo);

        // when
        TodoResponse response = todoService.updateTodoProgress(todoId, 50, userId);

        // then
        assertThat(response).isNotNull();
        then(todoRepository).should().findById(todoId);
        then(todoRepository).should().save(any(Todo.class));
    }

    @Test
    @DisplayName("부모 할일 진행률 자동 계산 - 서브태스크 진행률 평균")
    void updateParentProgress_AutoCalculation() {
        // given
        given(todoRepository.findById(subtask1.getTodoId())).willReturn(Optional.of(subtask1));
        given(todoRepository.findById(parentTodoId)).willReturn(Optional.of(parentTodo));
        given(todoRepository.findByParentTodoId(parentTodoId)).willReturn(List.of(subtask1, subtask2));
        given(todoRepository.save(any(Todo.class))).willReturn(subtask1, parentTodo);

        // when - subtask1의 진행률을 80%로 변경
        todoService.updateTodoProgress(subtask1.getTodoId(), 80, userId);

        // then - 부모의 진행률은 (80 + 100) / 2 = 90이 되어야 함
        then(todoRepository).should(times(2)).save(any(Todo.class));
    }

    @Test
    @DisplayName("할일 진행률 변경 시 상태 자동 업데이트 - 0%는 TODO")
    void updateTodoProgress_StatusAutoUpdate_Zero() {
        // given
        testTodo.setProgressPercentage(50);
        testTodo.setStatus(TodoStatus.IN_PROGRESS);
        given(todoRepository.findById(todoId)).willReturn(Optional.of(testTodo));
        given(todoRepository.save(any(Todo.class))).willReturn(testTodo);

        // when
        todoService.updateTodoProgress(todoId, 0, userId);

        // then - 진행률 0%는 상태가 TODO로 변경되어야 함
        then(todoRepository).should().save(argThat(todo ->
                todo.getProgressPercentage() == 0 && todo.getStatus() == TodoStatus.TODO
        ));
    }

    @Test
    @DisplayName("할일 진행률 변경 시 상태 자동 업데이트 - 100%는 DONE")
    void updateTodoProgress_StatusAutoUpdate_Complete() {
        // given
        given(todoRepository.findById(todoId)).willReturn(Optional.of(testTodo));
        given(todoRepository.save(any(Todo.class))).willReturn(testTodo);

        // when
        todoService.updateTodoProgress(todoId, 100, userId);

        // then - 진행률 100%는 상태가 DONE으로 변경되어야 함
        then(todoRepository).should().save(argThat(todo ->
                todo.getProgressPercentage() == 100 && todo.getStatus() == TodoStatus.DONE
        ));
    }

    @Test
    @DisplayName("할일 진행률 변경 시 상태 자동 업데이트 - 1-99%는 IN_PROGRESS")
    void updateTodoProgress_StatusAutoUpdate_InProgress() {
        // given
        given(todoRepository.findById(todoId)).willReturn(Optional.of(testTodo));
        given(todoRepository.save(any(Todo.class))).willReturn(testTodo);

        // when
        todoService.updateTodoProgress(todoId, 50, userId);

        // then - 진행률 1-99%는 상태가 IN_PROGRESS로 변경되어야 함
        then(todoRepository).should().save(argThat(todo ->
                todo.getProgressPercentage() == 50 && todo.getStatus() == TodoStatus.IN_PROGRESS
        ));
    }

    @Test
    @DisplayName("할일 삭제 성공")
    void deleteTodo_Success() {
        // given
        given(todoRepository.findById(todoId)).willReturn(Optional.of(testTodo));
        willDoNothing().given(todoRepository).delete(testTodo);

        // when
        todoService.deleteTodo(todoId, userId);

        // then
        then(todoRepository).should().findById(todoId);
        then(todoRepository).should().delete(testTodo);
    }

    @Test
    @DisplayName("할일 삭제 실패 - 권한 없음")
    void deleteTodo_Unauthorized() {
        // given
        Long unauthorizedUserId = 999L;
        given(todoRepository.findById(todoId)).willReturn(Optional.of(testTodo));

        // when & then
        assertThatThrownBy(() -> todoService.deleteTodo(todoId, unauthorizedUserId))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("해당 할일에 접근할 권한이 없습니다");

        then(todoRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("날짜 범위로 할일 조회 성공")
    void getTodosByDateRange_Success() {
        // given
        LocalDate startDate = LocalDate.of(2025, 11, 1);
        LocalDate endDate = LocalDate.of(2025, 11, 30);
        List<Todo> todos = List.of(testTodo);
        given(todoRepository.findByUserIdAndDateRange(userId, startDate, endDate))
                .willReturn(todos);

        // when
        List<TodoResponse> responses = todoService.getTodosByDateRange(userId, startDate, endDate);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getTodoId()).isEqualTo(todoId);

        then(todoRepository).should().findByUserIdAndDateRange(userId, startDate, endDate);
    }
}
