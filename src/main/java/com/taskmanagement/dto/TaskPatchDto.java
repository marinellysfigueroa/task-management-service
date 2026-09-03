package com.taskmanagement.dto;

import com.taskmanagement.model.TaskPriority;
import com.taskmanagement.model.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Partial update payload for {@code PATCH /api/v1/tasks/{id}}.
 *
 * <p>Every field is optional: {@code null} means "leave unchanged". The most
 * common real-world call — moving a task along its lifecycle — becomes
 * {@code {"status": "IN_PROGRESS"}} instead of a full round-trip of the resource,
 * which also removes the lost-update window a read-modify-PUT cycle would open.
 *
 * <p>Known limitation of the null-as-absent convention: a PATCH cannot clear
 * {@code assigneeId} or {@code dueDate}. Clearing is done with a PUT, which
 * replaces the resource wholesale. The alternative (JsonNullable tri-state
 * wrappers) was not worth the extra type noise at this size.
 *
 * <p>Note {@code projectId} is absent on purpose: moving a task to another
 * project changes which aggregate owns it, so it is treated as a full
 * replacement (PUT) rather than an incidental field tweak.
 */
@Schema(name = "TaskPatch", description = "Partial task update; omitted fields keep their current value")
public record TaskPatchDto(

        @Schema(description = "New title", example = "Set up the CI/CD pipeline")
        @Size(min = 1, max = 200, message = "title must be between 1 and 200 characters")
        String title,

        @Schema(description = "New description", example = "Also publish the container image to ECR")
        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @Schema(description = "New lifecycle status", example = "IN_PROGRESS")
        TaskStatus status,

        @Schema(description = "New priority", example = "CRITICAL")
        TaskPriority priority,

        @Schema(description = "Reassign the task to another user", example = "43")
        @Positive(message = "assigneeId must be a positive number")
        Long assigneeId,

        @Schema(description = "New due date, ISO-8601", example = "2027-01-15")
        LocalDate dueDate
) {
}
