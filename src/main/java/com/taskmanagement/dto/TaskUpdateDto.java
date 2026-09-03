package com.taskmanagement.dto;

import com.taskmanagement.model.TaskPriority;
import com.taskmanagement.model.TaskStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Fields are optional here; only non-null values are applied by the service
 * layer, enabling partial (PATCH-like) updates via a PUT endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskUpdateDto {

    @Size(max = 200, message = "title must be at most 200 characters")
    private String title;

    @Size(max = 2000, message = "description must be at most 2000 characters")
    private String description;

    private TaskStatus status;

    private TaskPriority priority;

    private Long assigneeId;

    private LocalDate dueDate;
}
