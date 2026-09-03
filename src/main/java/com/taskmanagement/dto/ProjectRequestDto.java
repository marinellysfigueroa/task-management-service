package com.taskmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Full representation of a project as supplied by the client on create (POST)
 * and full replace (PUT).
 *
 * <p>Design decision: request DTOs are Java records, i.e. immutable and without
 * an {@code id}. Server-controlled state ({@code id}, {@code createdAt}) is
 * never accepted from the client, which removes a whole class of mass-assignment
 * bugs. Keeping this type separate from the {@code Project} entity also means the
 * persistence model can evolve without silently breaking the HTTP contract.
 */
@Schema(name = "ProjectRequest", description = "Payload used to create or fully replace a project")
public record ProjectRequestDto(

        @Schema(description = "Human readable project name", example = "Platform Migration", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Schema(description = "Free-form description of the project's goal", example = "Migrate the legacy monolith to the new service platform")
        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @Schema(description = "Identifier of the user who owns the project", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "ownerId is required")
        @Positive(message = "ownerId must be a positive number")
        Long ownerId
) {
}
