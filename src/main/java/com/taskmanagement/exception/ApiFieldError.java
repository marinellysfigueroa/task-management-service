package com.taskmanagement.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single field-level validation failure, attached to a validation
 * {@code ProblemDetail} under the {@code errors} extension member.
 *
 * <p>RFC 7807 deliberately leaves the description of multiple errors to the API,
 * and recommends extension members for exactly this. Returning a structured
 * array — rather than a concatenated string — lets a UI highlight the offending
 * input without string parsing.
 */
@Schema(name = "ApiFieldError", description = "A single field-level validation failure")
public record ApiFieldError(

        @Schema(description = "Name of the offending field, using dot notation for nested paths", example = "title")
        String field,

        @Schema(description = "Why the value was rejected", example = "title is required")
        String message,

@Schema(description = "The rejected value, omitted when it cannot be safely echoed")
        Object rejectedValue
) {
}
