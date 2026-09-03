package com.taskmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Outbound representation of a project.
 *
 * <p>Design decision: the {@code tasks} collection is intentionally omitted. The
 * entity maps it as a lazy {@code @OneToMany}, and exposing it here would either
 * force an extra fetch on every read or blow up an unbounded list into the
 * response. Tasks are reachable through {@code GET /api/v1/tasks?projectId=...},
 * which is paginated.
 */
@Schema(name = "ProjectResponse", description = "A project as returned by the API")
public record ProjectResponseDto(

        @Schema(description = "Unique project identifier", example = "1")
        Long id,

        @Schema(description = "Human readable project name", example = "Platform Migration")
        String name,

        @Schema(description = "Free-form description of the project's goal", example = "Migrate the legacy monolith to the new service platform")
        String description,

        @Schema(description = "Identifier of the user who owns the project", example = "42")
        Long ownerId,

        @Schema(description = "Server-assigned creation timestamp (UTC)", example = "2026-09-03T10:15:30")
        LocalDateTime createdAt
) {
}
