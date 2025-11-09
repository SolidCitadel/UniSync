package com.unisync.schedule.todos.controller;

import com.unisync.schedule.todos.dto.TodoRequest;
import com.unisync.schedule.todos.dto.TodoResponse;
import com.unisync.schedule.todos.dto.UpdateTodoProgressRequest;
import com.unisync.schedule.todos.dto.UpdateTodoStatusRequest;
import com.unisync.schedule.todos.service.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/todos")
@RequiredArgsConstructor
@Tag(name = "Todo", description = "할일 관리 API")
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    @Operation(summary = "할일 목록 조회")
    public ResponseEntity<List<TodoResponse>> getTodos(
            @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<TodoResponse> todos;
        if (startDate != null && endDate != null) {
            todos = todoService.getTodosByDateRange(cognitoSub, startDate, endDate);
        } else {
            todos = todoService.getTodosByUserId(cognitoSub);
        }
        return ResponseEntity.ok(todos);
    }

    @GetMapping("/{todoId}")
    @Operation(summary = "할일 상세 조회")
    public ResponseEntity<TodoResponse> getTodoById(@PathVariable Long todoId) {
        return ResponseEntity.ok(todoService.getTodoById(todoId));
    }

    @GetMapping("/{todoId}/subtasks")
    @Operation(summary = "서브태스크 목록 조회")
    public ResponseEntity<List<TodoResponse>> getSubtasks(@PathVariable Long todoId) {
        return ResponseEntity.ok(todoService.getSubtasks(todoId));
    }

    @PostMapping
    @Operation(summary = "할일 생성")
    public ResponseEntity<TodoResponse> createTodo(
            @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody TodoRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(todoService.createTodo(request, cognitoSub));
    }

    @PostMapping("/{todoId}/subtasks")
    @Operation(summary = "서브태스크 생성")
    public ResponseEntity<TodoResponse> createSubtask(
            @PathVariable Long todoId,
            @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody TodoRequest request
    ) {
        request.setParentTodoId(todoId);
        return ResponseEntity.status(HttpStatus.CREATED).body(todoService.createTodo(request, cognitoSub));
    }

    @PutMapping("/{todoId}")
    @Operation(summary = "할일 수정")
    public ResponseEntity<TodoResponse> updateTodo(
            @PathVariable Long todoId,
            @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody TodoRequest request
    ) {
        return ResponseEntity.ok(todoService.updateTodo(todoId, request, cognitoSub));
    }

    @PatchMapping("/{todoId}/status")
    @Operation(summary = "할일 상태 변경")
    public ResponseEntity<TodoResponse> updateTodoStatus(
            @PathVariable Long todoId,
            @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody UpdateTodoStatusRequest request
    ) {
        return ResponseEntity.ok(todoService.updateTodoStatus(todoId, request.getStatus(), cognitoSub));
    }

    @PatchMapping("/{todoId}/progress")
    @Operation(summary = "할일 진행률 변경")
    public ResponseEntity<TodoResponse> updateTodoProgress(
            @PathVariable Long todoId,
            @RequestHeader("X-Cognito-Sub") String cognitoSub,
            @Valid @RequestBody UpdateTodoProgressRequest request
    ) {
        return ResponseEntity.ok(todoService.updateTodoProgress(todoId, request.getProgressPercentage(), cognitoSub));
    }

    @DeleteMapping("/{todoId}")
    @Operation(summary = "할일 삭제")
    public ResponseEntity<Void> deleteTodo(
            @PathVariable Long todoId,
            @RequestHeader("X-Cognito-Sub") String cognitoSub
    ) {
        todoService.deleteTodo(todoId, cognitoSub);
        return ResponseEntity.noContent().build();
    }
}
