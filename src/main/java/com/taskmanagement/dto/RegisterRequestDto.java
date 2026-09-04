package com.taskmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-registration payload for {@code POST /api/v1/auth/register}.
 *
 * <p>Design decision: there is deliberately no {@code role} field. Registration
 * always creates a {@code USER} — granting {@code ADMIN}/{@code MANAGER} is an
 * administrative act, performed afterwards by an existing admin through
 * {@code POST /api/v1/users}. Accepting a client-supplied role on a public,
 * unauthenticated endpoint would let anyone register as an administrator.
 */
@Schema(name = "RegisterRequest", description = "Self-registration payload; the account is always created with the USER role")
public record RegisterRequestDto(

        @Schema(description = "Unique login name", example = "mfigueroa", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50, message = "username must be between 3 and 50 characters")
        String username,

        @Schema(description = "Contact email, also used to detect duplicate accounts", example = "marinellys.figueroa@gmail.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @Schema(description = "Plaintext password; hashed with BCrypt before being stored", example = "S3curePass!23", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be at least 8 characters")
        String password
) {
}
