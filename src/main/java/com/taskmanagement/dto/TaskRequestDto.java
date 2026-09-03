package com.taskmanagement.dto;

import com.taskmanagement.model.TaskPriority;
import com.taskmanagement.model.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Full representation of a task as supplied by the client on create (POST) and
 * full replace (PUT).
 *
 * <p>Design decision: relations are expressed as plain identifiers
 * ({@code projectId}, {@code assigneeId}) rather than nested objects. The client
 * never gets to push a graph of entities into the persistence layer; the service
 * resolves each id against its repository and fails with 404 when it does not
 * exist. This keeps the write model flat and the aggregate boundaries explicit.
 *
 * <p>Because PUT replaces the whole resource, omitting {@code assigneeId} on a
 * PUT unassigns the task. Use PATCH when you only mean to touch some fields.
 */
@Schema(name = "TaskRequest", description = "Payload used to create or fully replace a task")
public record TaskRequestDto(

        @Schema(description = "Short summary of the work to be done", example = "Set up the CI pipeline", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @Schema(description = "Detailed description of the task", example = "Configure build, test and container publishing stages")
        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @Schema(description = "Lifecycle status; defaults to TODO when omitted", example = "TODO")
        TaskStatus status,

        @Schema(description = "Priority level; defaults to MEDIUM when omitted", example = "HIGH")
        TaskPriority priority,

        @Schema(description = "Project the task belongs to", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "projectId is required")
        @Positive(message = "projectId must be a positive number")
        Long projectId,

        @Schema(description = "User the task is assigned to; null leaves the task unassigned", example = "42")
        @Positive(message = "assigneeId must be a positive number")
        Long assigneeId,

        @Schema(description = "Date the task is due, ISO-8601", example = "2026-12-31")
        LocalDate dueDate
) {
}
