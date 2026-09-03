package com.taskmanagement.controller;

import com.taskmanagement.dto.TaskRequestDto;
import com.taskmanagement.dto.TaskResponseDto;
import com.taskmanagement.dto.TaskUpdateDto;
import com.taskmanagement.model.TaskStatus;
import com.taskmanagement.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Task management endpoints")
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @Operation(summary = "Create a new task")
    public ResponseEntity<TaskResponseDto> createTask(@Valid @RequestBody TaskRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task by id")
    public ResponseEntity<TaskResponseDto> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @GetMapping
    @Operation(summary = "List tasks, optionally filtered by project, assignee or status")
    public ResponseEntity<List<TaskResponseDto>> getTasks(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) TaskStatus status) {
        if (projectId != null) {
            return ResponseEntity.ok(taskService.getTasksByProject(projectId));
        }
        if (assigneeId != null) {
            return ResponseEntity.ok(taskService.getTasksByAssignee(assigneeId));
        }
        if (status != null) {
            return ResponseEntity.ok(taskService.getTasksByStatus(status));
        }
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing task (partial update)")
    public ResponseEntity<TaskResponseDto> updateTask(@PathVariable Long id, @Valid @RequestBody TaskUpdateDto request) {
        return ResponseEntity.ok(taskService.updateTask(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a task")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
