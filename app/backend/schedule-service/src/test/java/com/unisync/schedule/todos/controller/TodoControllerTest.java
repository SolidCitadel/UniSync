package com.unisync.schedule.todos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unisync.schedule.common.entity.Todo.TodoPriority;
import com.unisync.schedule.common.entity.Todo.TodoStatus;
// Note: TodoResponse uses String for status/priority, not enum
import com.unisync.schedule.todos.dto.TodoRequest;
import com.unisync.schedule.todos.dto.TodoResponse;
import com.unisync.schedule.todos.dto.UpdateTodoProgressRequest;
import com.unisync.schedule.todos.dto.UpdateTodoStatusRequest;
import com.unisync.schedule.todos.exception.TodoNotFoundException;
import com.unisync.schedule.todos.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TodoController 단위 테스트
 */
@WebMvcTest(TodoController.class)
@DisplayName("TodoController 단위 테스트")
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TodoService todoService;

    private ObjectMapper objectMapper;
    private static final String COGNITO_SUB = "test-user-cognito-sub";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ========================================
    // GET /v1/todos 테스트
    // ========================================

    @Test
    @DisplayName("GET /v1/todos - 할일 목록 조회 성공")
    void getTodos_Success() throws Exception {
        // Given
        List<TodoResponse> todos = Arrays.asList(
                TodoResponse.builder()
                        .todoId(1L)
                        .cognitoSub(COGNITO_SUB)
                        .title("과제 제출")
                        .status(TodoStatus.TODO)
                        .priority(TodoPriority.HIGH)
                        .build(),
                TodoResponse.builder()
                        .todoId(2L)
                        .cognitoSub(COGNITO_SUB)
                        .title("독서")
                        .status(TodoStatus.IN_PROGRESS)
                        .priority(TodoPriority.MEDIUM)
                        .build()
        );

        given(todoService.getTodos(COGNITO_SUB, null, false, null, null, null, null))
                .willReturn(todos);

        // When & Then
        mockMvc.perform(get("/v1/todos")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("과제 제출"))
                .andExpect(jsonPath("$[1].title").value("독서"));

        then(todoService).should().getTodos(COGNITO_SUB, null, false, null, null, null, null);
    }

    @Test
    @DisplayName("GET /v1/todos - 날짜 범위로 조회")
    void getTodos_WithDateRange() throws Exception {
        // Given
        List<TodoResponse> todos = Collections.singletonList(
                TodoResponse.builder()
                        .todoId(1L)
                        .title("11월 할일")
                        .build()
        );

        given(todoService.getTodos(eq(COGNITO_SUB), eq(null), eq(false), any(LocalDate.class), any(LocalDate.class), eq(null), eq(null)))
                .willReturn(todos);

        // When & Then
        mockMvc.perform(get("/v1/todos")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .param("startDate", "2025-11-01")
                        .param("endDate", "2025-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        then(todoService).should().getTodos(eq(COGNITO_SUB), eq(null), eq(false), any(LocalDate.class), any(LocalDate.class), eq(null), eq(null));
    }

    // ========================================
    // GET /v1/todos/{todoId} 테스트
    // ========================================

    @Test
    @DisplayName("GET /v1/todos/{todoId} - 할일 상세 조회 성공")
    void getTodoById_Success() throws Exception {
        // Given
        TodoResponse todo = TodoResponse.builder()
                .todoId(1L)
                .cognitoSub(COGNITO_SUB)
                .categoryId(10L)
                .title("과제 제출")
                .description("데이터베이스 설계 보고서")
                .startDate(LocalDate.of(2025, 11, 1))
                .dueDate(LocalDate.of(2025, 11, 15))
                .status(TodoStatus.TODO)
                .priority(TodoPriority.HIGH)
                .progressPercentage(0)
                .build();

        given(todoService.getTodoById(1L))
                .willReturn(todo);

        // When & Then
        mockMvc.perform(get("/v1/todos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todoId").value(1))
                .andExpect(jsonPath("$.title").value("과제 제출"))
                .andExpect(jsonPath("$.priority").value("HIGH"));

        then(todoService).should().getTodoById(1L);
    }

    @Test
    @DisplayName("GET /v1/todos/{todoId} - 존재하지 않는 할일 404")
    void getTodoById_NotFound() throws Exception {
        // Given
        given(todoService.getTodoById(999L))
                .willThrow(new TodoNotFoundException("할일을 찾을 수 없습니다: 999"));

        // When & Then
        mockMvc.perform(get("/v1/todos/999"))
                .andExpect(status().isNotFound());
    }

    // ========================================
    // GET /v1/todos/{todoId}/subtasks 테스트
    // ========================================

    @Test
    @DisplayName("GET /v1/todos/{todoId}/subtasks - 서브태스크 조회 성공")
    void getSubtasks_Success() throws Exception {
        // Given
        List<TodoResponse> subtasks = Arrays.asList(
                TodoResponse.builder()
                        .todoId(2L)
                        .parentTodoId(1L)
                        .title("자료 조사")
                        .build(),
                TodoResponse.builder()
                        .todoId(3L)
                        .parentTodoId(1L)
                        .title("보고서 작성")
                        .build()
        );

        given(todoService.getSubtasks(1L))
                .willReturn(subtasks);

        // When & Then
        mockMvc.perform(get("/v1/todos/1/subtasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("자료 조사"));

        then(todoService).should().getSubtasks(1L);
    }

    // ========================================
    // POST /v1/todos 테스트
    // ========================================

    @Test
    @DisplayName("POST /v1/todos - 할일 생성 성공")
    void createTodo_Success() throws Exception {
        // Given
        TodoRequest request = new TodoRequest();
        request.setCategoryId(10L);
        request.setTitle("새 할일");
        request.setDescription("테스트 할일");
        request.setStartDate(LocalDate.of(2025, 11, 1));
        request.setDueDate(LocalDate.of(2025, 11, 10));
        request.setPriority(TodoPriority.HIGH);

        TodoResponse response = TodoResponse.builder()
                .todoId(1L)
                .cognitoSub(COGNITO_SUB)
                .categoryId(10L)
                .title("새 할일")
                .status(TodoStatus.TODO)
                .priority(TodoPriority.HIGH)
                .build();

        given(todoService.createTodo(any(TodoRequest.class), eq(COGNITO_SUB)))
                .willReturn(response);

        // When & Then
        mockMvc.perform(post("/v1/todos")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.todoId").value(1))
                .andExpect(jsonPath("$.title").value("새 할일"));

        then(todoService).should().createTodo(any(TodoRequest.class), eq(COGNITO_SUB));
    }

    // ========================================
    // POST /v1/todos/{todoId}/subtasks 테스트
    // ========================================

    @Test
    @DisplayName("POST /v1/todos/{todoId}/subtasks - 서브태스크 생성 성공")
    void createSubtask_Success() throws Exception {
        // Given
        TodoRequest request = new TodoRequest();
        request.setCategoryId(10L);
        request.setTitle("서브태스크");
        request.setDescription("부모 할일의 서브태스크");
        request.setStartDate(LocalDate.of(2025, 11, 1));
        request.setDueDate(LocalDate.of(2025, 11, 5));

        TodoResponse response = TodoResponse.builder()
                .todoId(2L)
                .parentTodoId(1L)
                .title("서브태스크")
                .status(TodoStatus.TODO)
                .build();

        given(todoService.createTodo(any(TodoRequest.class), eq(COGNITO_SUB)))
                .willReturn(response);

        // When & Then
        mockMvc.perform(post("/v1/todos/1/subtasks")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.todoId").value(2))
                .andExpect(jsonPath("$.parentTodoId").value(1));

        then(todoService).should().createTodo(any(TodoRequest.class), eq(COGNITO_SUB));
    }

    // ========================================
    // PUT /v1/todos/{todoId} 테스트
    // ========================================

    @Test
    @DisplayName("PUT /v1/todos/{todoId} - 할일 수정 성공")
    void updateTodo_Success() throws Exception {
        // Given
        TodoRequest request = new TodoRequest();
        request.setCategoryId(10L);
        request.setTitle("수정된 할일");
        request.setDescription("수정된 설명");
        request.setStartDate(LocalDate.of(2025, 11, 1));
        request.setDueDate(LocalDate.of(2025, 11, 20));
        request.setPriority(TodoPriority.MEDIUM);

        TodoResponse response = TodoResponse.builder()
                .todoId(1L)
                .title("수정된 할일")
                .priority(TodoPriority.MEDIUM)
                .build();

        given(todoService.updateTodo(eq(1L), any(TodoRequest.class), eq(COGNITO_SUB)))
                .willReturn(response);

        // When & Then
        mockMvc.perform(put("/v1/todos/1")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 할일"));

        then(todoService).should().updateTodo(eq(1L), any(TodoRequest.class), eq(COGNITO_SUB));
    }

    // ========================================
    // PATCH /v1/todos/{todoId}/status 테스트
    // ========================================

    @Test
    @DisplayName("PATCH /v1/todos/{todoId}/status - 상태 변경 성공")
    void updateTodoStatus_Success() throws Exception {
        // Given
        UpdateTodoStatusRequest request = new UpdateTodoStatusRequest();
        request.setStatus(TodoStatus.DONE);

        TodoResponse response = TodoResponse.builder()
                .todoId(1L)
                .title("완료된 할일")
                .status(TodoStatus.DONE)
                .build();

        given(todoService.updateTodoStatus(eq(1L), eq(TodoStatus.DONE), eq(COGNITO_SUB)))
                .willReturn(response);

        // When & Then
        mockMvc.perform(patch("/v1/todos/1/status")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        then(todoService).should().updateTodoStatus(eq(1L), eq(TodoStatus.DONE), eq(COGNITO_SUB));
    }

    // ========================================
    // PATCH /v1/todos/{todoId}/progress 테스트
    // ========================================

    @Test
    @DisplayName("PATCH /v1/todos/{todoId}/progress - 진행률 변경 성공")
    void updateTodoProgress_Success() throws Exception {
        // Given
        UpdateTodoProgressRequest request = new UpdateTodoProgressRequest();
        request.setProgressPercentage(75);

        TodoResponse response = TodoResponse.builder()
                .todoId(1L)
                .title("진행 중인 할일")
                .progressPercentage(75)
                .status(TodoStatus.IN_PROGRESS)
                .build();

        given(todoService.updateTodoProgress(eq(1L), eq(75), eq(COGNITO_SUB)))
                .willReturn(response);

        // When & Then
        mockMvc.perform(patch("/v1/todos/1/progress")
                        .header("X-Cognito-Sub", COGNITO_SUB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercentage").value(75));

        then(todoService).should().updateTodoProgress(eq(1L), eq(75), eq(COGNITO_SUB));
    }

    // ========================================
    // DELETE /v1/todos/{todoId} 테스트
    // ========================================

    @Test
    @DisplayName("DELETE /v1/todos/{todoId} - 할일 삭제 성공")
    void deleteTodo_Success() throws Exception {
        // Given
        willDoNothing().given(todoService).deleteTodo(1L, COGNITO_SUB);

        // When & Then
        mockMvc.perform(delete("/v1/todos/1")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isNoContent());

        then(todoService).should().deleteTodo(1L, COGNITO_SUB);
    }

    @Test
    @DisplayName("DELETE /v1/todos/{todoId} - 존재하지 않는 할일 삭제 시 404")
    void deleteTodo_NotFound() throws Exception {
        // Given
        willThrow(new TodoNotFoundException("할일을 찾을 수 없습니다: 999"))
                .given(todoService).deleteTodo(999L, COGNITO_SUB);

        // When & Then
        mockMvc.perform(delete("/v1/todos/999")
                        .header("X-Cognito-Sub", COGNITO_SUB))
                .andExpect(status().isNotFound());
    }
}
