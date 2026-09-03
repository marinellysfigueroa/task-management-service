package com.taskmanagement.controller;

import com.taskmanagement.config.ApiExamples;
import com.taskmanagement.dto.PageResponse;
import com.taskmanagement.dto.TaskFilter;
import com.taskmanagement.dto.TaskPatchDto;
import com.taskmanagement.dto.TaskRequestDto;
import com.taskmanagement.dto.TaskResponseDto;
import com.taskmanagement.model.TaskPriority;
import com.taskmanagement.model.TaskStatus;
import com.taskmanagement.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * HTTP entry point for the task resource.
 *
 * <p>See {@link ProjectController} for the shared conventions (URI versioning,
 * thin-adapter controllers, DTO-only responses).
 */
@RestController
@RequestMapping(value = "/api/v1/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
@Tag(name = "Tasks", description = "Create, query and maintain tasks")
public class TaskController {

    private final TaskService taskService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a task",
            description = """
                    Creates a task inside an existing project. `status` defaults to `TODO`
                    and `priority` to `MEDIUM` when omitted, and `assigneeId` may be left
                    out to create an unassigned task. On success the `Location` header
                    points at the newly created resource.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponseDto.class),
                            examples = @ExampleObject(name = "Created task", value = ApiExamples.TASK_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "Payload failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Validation error", value = ApiExamples.PROBLEM_VALIDATION))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Unauthorized", value = ApiExamples.PROBLEM_UNAUTHORIZED))),
            @ApiResponse(responseCode = "404", description = "The referenced project or assignee does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Unknown project", value = ApiExamples.PROBLEM_PROJECT_NOT_FOUND)))
    })
    public ResponseEntity<TaskResponseDto> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Task to create",
                    content = @Content(examples = {
                            @ExampleObject(name = "Full payload", description = "Every field supplied explicitly",
                                    value = ApiExamples.TASK_CREATE_REQUEST),
                            @ExampleObject(name = "Minimal payload", description = "Only the required fields; status and priority fall back to their defaults",
                                    value = ApiExamples.TASK_MINIMAL_CREATE_REQUEST)
                    }))
            @Valid @RequestBody TaskRequestDto request) {

        TaskResponseDto created = taskService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task by id", description = "Returns a single task.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponseDto.class),
                            examples = @ExampleObject(name = "Task", value = ApiExamples.TASK_RESPONSE))),
            @ApiResponse(responseCode = "404", description = "No task with that id",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Not found", value = ApiExamples.PROBLEM_TASK_NOT_FOUND)))
    })
    public ResponseEntity<TaskResponseDto> getById(
            @Parameter(description = "Task identifier", example = "10", required = true)
            @PathVariable @Positive(message = "id must be a positive number") Long id) {

        return ResponseEntity.ok(taskService.findById(id));
    }

    /**
     * Filtering and pagination live on one endpoint on purpose. The alternative
     * — {@code /tasks/by-status/{status}}, {@code /tasks/by-project/{id}} — turns
     * every new criterion into a new URI and makes combinations impossible;
     * filters belong in the query string, which is exactly what it is for.
     *
     * <p>The individual criteria are declared as explicit parameters so that each
     * one is documented and type-converted by Spring (an invalid enum yields a
     * 400 naming the accepted values), and are then bundled into a
     * {@link TaskFilter} before crossing into the service layer.
     */
    @GetMapping
    @Operation(
            summary = "List tasks with filtering and pagination",
            description = """
                    Returns a page of tasks. All filters are optional and are combined with
                    AND, so `?projectId=1&status=IN_PROGRESS&assigneeId=42` means "the
                    in-progress tasks of project 1 assigned to user 42".

                    Pagination is always applied. `sort` accepts `id`, `title`, `status`,
                    `priority` and `dueDate` — for example `sort=dueDate,asc`; any other
                    property is rejected with 400 rather than ignored.

                    Examples:
                    - `GET /api/v1/tasks?status=TODO&page=0&size=20`
                    - `GET /api/v1/tasks?projectId=1&sort=priority,desc`
                    - `GET /api/v1/tasks?assigneeId=42&status=IN_PROGRESS`""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of tasks matching the filters",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(name = "First page", value = ApiExamples.TASK_PAGE_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "Unknown enum value or unsupported sort property",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Invalid status", value = ApiExamples.PROBLEM_INVALID_ENUM))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid credentials",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Unauthorized", value = ApiExamples.PROBLEM_UNAUTHORIZED)))
    })
    public ResponseEntity<PageResponse<TaskResponseDto>> search(
            @Parameter(description = "Keep only tasks in this lifecycle status", example = "IN_PROGRESS")
            @RequestParam(required = false) TaskStatus status,

            @Parameter(description = "Keep only tasks belonging to this project", example = "1")
            @RequestParam(required = false) @Positive(message = "projectId must be a positive number") Long projectId,

            @Parameter(description = "Keep only tasks assigned to this user", example = "42")
            @RequestParam(required = false) @Positive(message = "assigneeId must be a positive number") Long assigneeId,

            @Parameter(description = "Keep only tasks with this priority", example = "HIGH")
            @RequestParam(required = false) TaskPriority priority,

            @ParameterObject
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        TaskFilter filter = new TaskFilter(status, projectId, assigneeId, priority);
        return ResponseEntity.ok(taskService.search(filter, pageable));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Replace a task",
            description = """
                    Full replacement: every writable field is overwritten with the payload.
                    Omitting `assigneeId` therefore unassigns the task, and supplying a
                    different `projectId` moves it to another project. Use PATCH to change
                    a subset of the fields.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task replaced",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponseDto.class),
                            examples = @ExampleObject(name = "Updated task", value = ApiExamples.TASK_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "Payload failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Validation error", value = ApiExamples.PROBLEM_VALIDATION))),
            @ApiResponse(responseCode = "404", description = "Task, project or assignee not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Not found", value = ApiExamples.PROBLEM_TASK_NOT_FOUND)))
    })
    public ResponseEntity<TaskResponseDto> replace(
            @Parameter(description = "Task identifier", example = "10", required = true)
            @PathVariable @Positive(message = "id must be a positive number") Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Complete new state of the task",
                    content = @Content(examples = @ExampleObject(name = "Replacement", value = ApiExamples.TASK_REPLACE_REQUEST)))
            @Valid @RequestBody TaskRequestDto request) {

        return ResponseEntity.ok(taskService.replace(id, request));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Partially update a task",
            description = """
                    Applies only the fields present in the payload; omitted fields keep
                    their current value. This is the endpoint for the common lifecycle
                    move — `{"status": "DONE"}` — without round-tripping the whole
                    resource.

                    `assigneeId` and `dueDate` cannot be cleared through PATCH, because an
                    omitted field and an explicit null are indistinguishable here; use PUT
                    to clear them. `projectId` is not accepted: moving a task between
                    projects is a full replacement.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponseDto.class),
                            examples = @ExampleObject(name = "Updated task", value = ApiExamples.TASK_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "Payload failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Validation error", value = ApiExamples.PROBLEM_VALIDATION))),
            @ApiResponse(responseCode = "404", description = "Task or assignee not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Not found", value = ApiExamples.PROBLEM_TASK_NOT_FOUND)))
    })
    public ResponseEntity<TaskResponseDto> patch(
            @Parameter(description = "Task identifier", example = "10", required = true)
            @PathVariable @Positive(message = "id must be a positive number") Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Fields to change",
                    content = @Content(examples = @ExampleObject(name = "Move to in progress", value = ApiExamples.TASK_PATCH_REQUEST)))
            @Valid @RequestBody TaskPatchDto patch) {

        return ResponseEntity.ok(taskService.patch(id, patch));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a task",
            description = "Permanently removes a task. Deleting a task never affects its project.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task deleted; no response body"),
            @ApiResponse(responseCode = "404", description = "No task with that id",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Not found", value = ApiExamples.PROBLEM_TASK_NOT_FOUND)))
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Task identifier", example = "10", required = true)
            @PathVariable @Positive(message = "id must be a positive number") Long id) {

        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
