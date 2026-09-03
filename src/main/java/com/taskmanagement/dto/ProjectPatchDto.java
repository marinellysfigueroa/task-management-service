package com.taskmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Partial update payload for {@code PATCH /api/v1/projects/{id}}.
 *
 * <p>Design decision: PUT and PATCH get <em>different</em> DTOs rather than one
 * lenient type. A PUT replaces the resource, so its payload must be complete and
 * every required field is validated; a PATCH only carries the fields the client
 * wants to change, so every field is optional and {@code null} means "leave as
 * is". Modelling both with a single half-validated DTO is what usually turns PUT
 * into an accidental PATCH.
 *
 * <p>Consequence of using {@code null} as the "absent" marker: this payload
 * cannot distinguish "not provided" from "set to null". That is acceptable here
 * because no project field is both optional and nullable — use PUT to clear the
 * description.
 */
@Schema(name = "ProjectPatch", description = "Partial project update; omitted fields keep their current value")
public record ProjectPatchDto(

        @Schema(description = "New project name", example = "Platform Migration - Phase 2")
        @Size(min = 1, max = 150, message = "name must be between 1 and 150 characters")
        String name,

        @Schema(description = "New description", example = "Now also covers the reporting subsystem")
        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @Schema(description = "Transfer ownership to another user", example = "43")
        @Positive(message = "ownerId must be a positive number")
        Long ownerId
) {
}
