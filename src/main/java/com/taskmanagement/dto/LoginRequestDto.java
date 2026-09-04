package com.taskmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials payload for {@code POST /api/v1/auth/login}.
 *
 * <p>No length/format constraints beyond "not blank": validating password shape
 * here would leak policy details to a caller who has not proven they own the
 * account, and the real check is delegated to
 * {@code AuthenticationManager}/BCrypt anyway.
 */
@Schema(name = "LoginRequest", description = "Username/password credentials exchanged for an access token")
public record LoginRequestDto(

        @Schema(description = "Login name", example = "mfigueroa", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "username is required")
        String username,

        @Schema(description = "Plaintext password", example = "S3curePass!23", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "password is required")
        String password
) {
}
