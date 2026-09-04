package com.taskmanagement.dto;

import com.taskmanagement.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response returned by both {@code /auth/register} and {@code /auth/login} —
 * registering logs the new user in immediately, so there is no reason to make
 * the client call login again right after.
 */
@Schema(name = "AuthResponse", description = "Issued access token plus the identity it represents")
public record AuthResponseDto(

        @Schema(description = "JWT to send as 'Authorization: Bearer <accessToken>' on subsequent requests",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtZmlndWVyb2EiLCJyb2xlIjoiVVNFUiJ9.dQw4w9WgXcQ")
        String accessToken,

        @Schema(description = "Authorization scheme to prefix the header value with", example = "Bearer")
        String tokenType,

        @Schema(description = "Token lifetime in seconds from the moment it was issued", example = "3600")
        long expiresInSeconds,

        @Schema(description = "Username the token identifies", example = "mfigueroa")
        String username,

        @Schema(description = "Role granted to this account", example = "USER")
        Role role
) {
}
