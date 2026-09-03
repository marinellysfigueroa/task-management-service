package com.taskmanagement.dto;

import com.taskmanagement.model.TaskPriority;
import com.taskmanagement.model.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Outbound representation of a task.
 *
 * <p>Design decision: relations are returned as identifiers only. Inlining the
 * project name or assignee username would dereference a lazy association per
 * row and turn any paginated listing into an N+1 query. If a client needs those
 * labels, the right fix is an explicit projection with a fetch join, not an
 * accidental one hidden in the mapper.
 */
@Schema(name = "TaskResponse", description = "A task as returned by the API")
public record TaskResponseDto(

        @Schema(description = "Unique task identifier", example = "10")
        Long id,

        @Schema(description = "Short summary of the work to be done", example = "Set up the CI pipeline")
        String title,

        @Schema(description = "Detailed description of the task", example = "Configure build, test and container publishing stages")
        String description,

        @Schema(description = "Lifecycle status", example = "IN_PROGRESS")
        TaskStatus status,

        @Schema(description = "Priority level", example = "HIGH")
        TaskPriority priority,

        @Schema(description = "Project the task belongs to", example = "1")
        Long projectId,

        @Schema(description = "User the task is assigned to, or null when unassigned", example = "42")
        Long assigneeId,

        @Schema(description = "Date the task is due, ISO-8601", example = "2026-12-31")
        LocalDate dueDate
) {
}
