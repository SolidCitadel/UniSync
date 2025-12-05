package com.unisync.schedule.todos.service;

import com.unisync.schedule.common.entity.Todo;
import com.unisync.schedule.common.entity.Todo.TodoPriority;
import com.unisync.schedule.common.entity.Todo.TodoStatus;
import com.unisync.schedule.common.exception.UnauthorizedAccessException;
import com.unisync.schedule.common.repository.CategoryRepository;
import com.unisync.schedule.common.repository.TodoRepository;
import com.unisync.schedule.internal.service.GroupPermissionService;
import com.unisync.schedule.internal.client.UserServiceClient;
import com.unisync.schedule.todos.dto.TodoRequest;
import com.unisync.schedule.todos.dto.TodoResponse;
import com.unisync.schedule.todos.dto.TodoWithSubtasksResponse;
import com.unisync.schedule.todos.exception.InvalidTodoException;
import com.unisync.schedule.todos.exception.TodoNotFoundException;
import com.unisync.schedule.categories.exception.CategoryNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TodoService {

    private final TodoRepository todoRepository;
    private final CategoryRepository categoryRepository;
    private final GroupPermissionService groupPermissionService;
    private final UserServiceClient userServiceClient;

    /**
     * 할일 생성
     */
    @Transactional
    public TodoResponse createTodo(TodoRequest request, String cognitoSub) {
        log.info("할일 생성 요청 - cognitoSub: {}, title: {}, groupId: {}", cognitoSub, request.getTitle(), request.getGroupId());

        // 그룹 할일인 경우 쓰기 권한 검증
        groupPermissionService.validateWritePermission(request.getGroupId(), cognitoSub);

        // 날짜 유효성 검증
        validateTodoDates(request.getStartDate(), request.getDueDate());
        validateDeadline(request.getDueDate(), request.getDeadline());

        // 카테고리 존재 여부 확인
        validateCategoryAccess(request.getCategoryId(), cognitoSub);

        // 부모 할일이 있는 경우 검증
        if (request.getParentTodoId() != null) {
            validateParentTodo(request.getParentTodoId(), cognitoSub);
        }

        // Todo 엔티티 생성
        Todo todo = Todo.builder()
                .cognitoSub(cognitoSub)
                .groupId(request.getGroupId())
                .categoryId(request.getCategoryId())
                .title(request.getTitle())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .dueDate(request.getDueDate())
                .deadline(request.getDeadline())
                .status(TodoStatus.TODO)
                .priority(request.getPriority())
                .progressPercentage(0)
                .parentTodoId(request.getParentTodoId())
                .scheduleId(request.getScheduleId())
                .isAiGenerated(false) // 수동 생성은 false
                .build();

        Todo savedTodo = todoRepository.save(todo);
        log.info("할일 생성 완료 - todoId: {}", savedTodo.getTodoId());

        // 부모 할일의 진행률 재계산
        if (savedTodo.getParentTodoId() != null) {
            updateParentProgress(savedTodo.getParentTodoId());
        }

        return TodoResponse.from(savedTodo);
    }

    /**
     * 할일 ID로 조회
     */
    @Transactional(readOnly = true)
    public TodoResponse getTodoById(Long todoId) {
        log.info("할일 조회 - todoId: {}", todoId);

        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new TodoNotFoundException("할일을 찾을 수 없습니다. ID: " + todoId));

        return TodoResponse.from(todo);
    }

    /**
     * 사용자의 모든 할일 조회
     */
    @Transactional(readOnly = true)
    public List<TodoResponse> getTodosByUserId(String cognitoSub) {
        log.info("사용자 할일 전체 조회 - cognitoSub: {}", cognitoSub);

        List<Todo> todos = todoRepository.findByCognitoSub(cognitoSub);

        return todos.stream()
                .map(TodoResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 사용자/그룹/통합 조회
     */
    @Transactional(readOnly = true)
    public List<TodoResponse> getTodos(
            String cognitoSub,
            Long groupId,
            Boolean includeGroups,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String priority
    ) {
        List<Todo> todos;

        if (groupId != null) {
            groupPermissionService.validateReadPermission(groupId, cognitoSub);
            todos = todoRepository.findByGroupId(groupId);
        } else if (Boolean.TRUE.equals(includeGroups)) {
            List<Long> groupIds = userServiceClient.getUserGroupIds(cognitoSub);
            if (groupIds.isEmpty()) {
                todos = todoRepository.findByCognitoSub(cognitoSub);
            } else {
                todos = todoRepository.findByCognitoSubOrGroupIdIn(cognitoSub, groupIds);
            }
        } else {
            todos = todoRepository.findByCognitoSub(cognitoSub);
        }

        List<Todo> filtered = applyFilters(todos, startDate, endDate, status, priority);

        return filtered.stream()
                .map(TodoResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 기간의 할일 조회
     */
    @Transactional(readOnly = true)
    public List<TodoResponse> getTodosByDateRange(String cognitoSub, LocalDate start, LocalDate end) {
        log.info("기간별 할일 조회 - cognitoSub: {}, start: {}, end: {}", cognitoSub, start, end);

        // 날짜 유효성 검증
        validateTodoDates(start, end);

        List<Todo> todos = todoRepository.findByCognitoSubAndDateRange(cognitoSub, start, end);

        return todos.stream()
                .map(TodoResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 서브태스크 조회
     */
    @Transactional(readOnly = true)
    public List<TodoResponse> getSubtasks(Long parentTodoId) {
        log.info("서브태스크 조회 - parentTodoId: {}", parentTodoId);

        // 부모 할일 존재 여부 확인
        todoRepository.findById(parentTodoId)
                .orElseThrow(() -> new TodoNotFoundException("부모 할일을 찾을 수 없습니다. ID: " + parentTodoId));

        List<Todo> subtasks = todoRepository.findByParentTodoId(parentTodoId);

        return subtasks.stream()
                .map(TodoResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 일정에 연결된 할일(서브태스크 포함) 조회
     */
    @Transactional(readOnly = true)
    public List<TodoWithSubtasksResponse> getTodosByScheduleIdWithSubtasks(Long scheduleId) {
        List<Todo> mainTodos = todoRepository.findByScheduleIdAndParentTodoIdIsNull(scheduleId);

        return mainTodos.stream()
                .map(this::buildTodoWithSubtasks)
                .collect(Collectors.toList());
    }

    private TodoWithSubtasksResponse buildTodoWithSubtasks(Todo todo) {
        List<Todo> subtasks = todoRepository.findByParentTodoId(todo.getTodoId());
        List<TodoWithSubtasksResponse> subtaskResponses = subtasks.stream()
                .map(this::buildTodoWithSubtasks)
                .collect(Collectors.toList());

        return TodoWithSubtasksResponse.from(todo, subtaskResponses);
    }

    /**
     * 할일 수정
     */
    @Transactional
    public TodoResponse updateTodo(Long todoId, TodoRequest request, String cognitoSub) {
        log.info("할일 수정 요청 - todoId: {}, cognitoSub: {}", todoId, cognitoSub);

        // 할일 조회 및 권한 확인
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new TodoNotFoundException("할일을 찾을 수 없습니다. ID: " + todoId));

        validateTodoOwnership(todo, cognitoSub);

        // 날짜 유효성 검증
        validateTodoDates(request.getStartDate(), request.getDueDate());
        validateDeadline(request.getDueDate(), request.getDeadline());

        // 카테고리 변경 시 존재 여부 확인
        if (!todo.getCategoryId().equals(request.getCategoryId())) {
            validateCategoryAccess(request.getCategoryId(), cognitoSub);
        }

        // 할일 정보 업데이트
        todo.setTitle(request.getTitle());
        todo.setDescription(request.getDescription());
        todo.setStartDate(request.getStartDate());
        todo.setDueDate(request.getDueDate());
        todo.setDeadline(request.getDeadline());
        todo.setCategoryId(request.getCategoryId());
        todo.setGroupId(request.getGroupId());
        todo.setPriority(request.getPriority());
        todo.setScheduleId(request.getScheduleId());

        Todo updatedTodo = todoRepository.save(todo);
        log.info("할일 수정 완료 - todoId: {}", todoId);

        return TodoResponse.from(updatedTodo);
    }

    /**
     * 할일 상태 변경
     */
    @Transactional
    public TodoResponse updateTodoStatus(Long todoId, TodoStatus status, String cognitoSub) {
        log.info("할일 상태 변경 요청 - todoId: {}, status: {}, cognitoSub: {}", todoId, status, cognitoSub);

        // 할일 조회 및 권한 확인
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new TodoNotFoundException("할일을 찾을 수 없습니다. ID: " + todoId));

        validateTodoOwnership(todo, cognitoSub);

        // 상태 업데이트
        todo.setStatus(status);

        // 상태에 따라 진행률 자동 설정
        if (status == TodoStatus.DONE) {
            todo.setProgressPercentage(100);
        } else if (status == TodoStatus.TODO) {
            todo.setProgressPercentage(0);
        }

        Todo updatedTodo = todoRepository.save(todo);
        log.info("할일 상태 변경 완료 - todoId: {}, status: {}", todoId, status);

        // 부모 할일의 진행률 재계산
        if (updatedTodo.getParentTodoId() != null) {
            updateParentProgress(updatedTodo.getParentTodoId());
        }

        return TodoResponse.from(updatedTodo);
    }

    /**
     * 할일 진행률 변경
     */
    @Transactional
    public TodoResponse updateTodoProgress(Long todoId, Integer progress, String cognitoSub) {
        log.info("할일 진행률 변경 요청 - todoId: {}, progress: {}%, cognitoSub: {}", todoId, progress, cognitoSub);

        // 진행률 유효성 검증
        if (progress < 0 || progress > 100) {
            throw new InvalidTodoException("진행률은 0에서 100 사이여야 합니다.");
        }

        // 할일 조회 및 권한 확인
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new TodoNotFoundException("할일을 찾을 수 없습니다. ID: " + todoId));

        validateTodoOwnership(todo, cognitoSub);

        // 진행률 업데이트
        todo.setProgressPercentage(progress);

        // 진행률에 따라 상태 자동 변경
        if (progress == 0) {
            todo.setStatus(TodoStatus.TODO);
        } else if (progress == 100) {
            todo.setStatus(TodoStatus.DONE);
        } else {
            todo.setStatus(TodoStatus.IN_PROGRESS);
        }

        Todo updatedTodo = todoRepository.save(todo);
        log.info("할일 진행률 변경 완료 - todoId: {}, progress: {}%", todoId, progress);

        // 부모 할일의 진행률 재계산
        if (updatedTodo.getParentTodoId() != null) {
            updateParentProgress(updatedTodo.getParentTodoId());
        }

        return TodoResponse.from(updatedTodo);
    }

    /**
     * 할일 삭제
     */
    @Transactional
    public void deleteTodo(Long todoId, String cognitoSub) {
        log.info("할일 삭제 요청 - todoId: {}, cognitoSub: {}", todoId, cognitoSub);

        // 할일 조회 및 권한 확인
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new TodoNotFoundException("할일을 찾을 수 없습니다. ID: " + todoId));

        validateTodoOwnership(todo, cognitoSub);

        // 서브태스크가 있는 경우 함께 삭제
        List<Todo> subtasks = todoRepository.findByParentTodoId(todoId);
        if (!subtasks.isEmpty()) {
            log.info("서브태스크 {}개 함께 삭제", subtasks.size());
            todoRepository.deleteAll(subtasks);
        }

        Long parentTodoId = todo.getParentTodoId();

        todoRepository.delete(todo);
        log.info("할일 삭제 완료 - todoId: {}", todoId);

        // 부모 할일의 진행률 재계산
        if (parentTodoId != null) {
            updateParentProgress(parentTodoId);
        }
    }

    /**
     * 부모 할일의 진행률 재계산
     */
    private void updateParentProgress(Long parentTodoId) {
        Todo parentTodo = todoRepository.findById(parentTodoId).orElse(null);
        if (parentTodo == null) {
            return;
        }

        List<Todo> subtasks = todoRepository.findByParentTodoId(parentTodoId);
        if (subtasks.isEmpty()) {
            // 서브태스크가 없으면 진행률 유지
            return;
        }

        // 서브태스크들의 평균 진행률 계산
        int totalProgress = subtasks.stream()
                .mapToInt(Todo::getProgressPercentage)
                .sum();
        int averageProgress = totalProgress / subtasks.size();

        parentTodo.setProgressPercentage(averageProgress);

        // 진행률에 따라 상태 자동 변경
        if (averageProgress == 0) {
            parentTodo.setStatus(TodoStatus.TODO);
        } else if (averageProgress == 100) {
            parentTodo.setStatus(TodoStatus.DONE);
        } else {
            parentTodo.setStatus(TodoStatus.IN_PROGRESS);
        }

        todoRepository.save(parentTodo);
        log.info("부모 할일 진행률 재계산 완료 - parentTodoId: {}, progress: {}%", parentTodoId, averageProgress);
    }

    private List<Todo> applyFilters(List<Todo> todos, LocalDate startDate, LocalDate endDate, String status, String priority) {
        TodoStatus statusFilter = parseStatus(status);
        TodoPriority priorityFilter = parsePriority(priority);

        return todos.stream()
                .filter(todo -> startDate == null || !todo.getStartDate().isBefore(startDate))
                .filter(todo -> endDate == null || !todo.getDueDate().isAfter(endDate))
                .filter(todo -> statusFilter == null || statusFilter.equals(todo.getStatus()))
                .filter(todo -> priorityFilter == null || priorityFilter.equals(todo.getPriority()))
                .collect(Collectors.toList());
    }

    private TodoStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return TodoStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidTodoException("유효하지 않은 상태 값입니다: " + status);
        }
    }

    private TodoPriority parsePriority(String priority) {
        if (priority == null) {
            return null;
        }
        try {
            return TodoPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidTodoException("유효하지 않은 우선순위 값입니다: " + priority);
        }
    }

    /**
     * 날짜 유효성 검증
     */
    private void validateTodoDates(LocalDate startDate, LocalDate dueDate) {
        if (startDate == null || dueDate == null) {
            throw new InvalidTodoException("시작 날짜와 마감 날짜는 필수입니다.");
        }

        if (dueDate.isBefore(startDate)) {
            throw new InvalidTodoException("마감 날짜는 시작 날짜보다 늦어야 합니다.");
        }
    }

    /**
     * 목표 완료일과 최종 마감일시 검증
     */
    private void validateDeadline(LocalDate dueDate, LocalDateTime deadline) {
        if (deadline == null || dueDate == null) {
            return;
        }

        LocalDate deadlineDate = deadline.toLocalDate();
        if (dueDate.isAfter(deadlineDate)) {
            throw new InvalidTodoException("목표 완료일(dueDate)은 최종 마감일(deadline)보다 빠르거나 같아야 합니다.");
        }
    }

    /**
     * 할일 소유권/권한 검증
     */
    private void validateTodoOwnership(Todo todo, String cognitoSub) {
        if (todo.getGroupId() != null) {
            // 그룹 할일: User-Service에서 권한 확인
            groupPermissionService.validateWritePermission(todo.getGroupId(), cognitoSub);
        } else if (!todo.getCognitoSub().equals(cognitoSub)) {
            // 개인 할일: cognitoSub 일치 확인
            throw new UnauthorizedAccessException("해당 할일에 접근할 권한이 없습니다.");
        }
    }

    /**
     * 카테고리 접근 권한 검증
     */
    private void validateCategoryAccess(Long categoryId, String cognitoSub) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("카테고리를 찾을 수 없습니다. ID: " + categoryId));

        // 추가적으로 카테고리가 해당 사용자 또는 그룹에 속하는지 검증 가능
        // 현재는 존재 여부만 확인
    }

    /**
     * 부모 할일 검증
     */
    private void validateParentTodo(Long parentTodoId, String cognitoSub) {
        Todo parentTodo = todoRepository.findById(parentTodoId)
                .orElseThrow(() -> new TodoNotFoundException("부모 할일을 찾을 수 없습니다. ID: " + parentTodoId));

        // 부모 할일의 소유자와 일치하는지 확인
        if (parentTodo.getGroupId() == null && !parentTodo.getCognitoSub().equals(cognitoSub)) {
            throw new UnauthorizedAccessException("부모 할일에 접근할 권한이 없습니다.");
        }
    }
}
