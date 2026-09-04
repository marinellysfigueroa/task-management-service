package com.taskmanagement.controller;

import com.taskmanagement.config.ApiExamples;
import com.taskmanagement.dto.AuthResponseDto;
import com.taskmanagement.dto.LoginRequestDto;
import com.taskmanagement.dto.RegisterRequestDto;
import com.taskmanagement.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only two endpoints reachable without a token: creating an account and
 * exchanging credentials for one.
 *
 * <p>{@code @SecurityRequirements} (empty) overrides the API-wide bearer
 * requirement declared in {@code OpenApiConfig} for just this controller, so
 * Swagger UI does not show a padlock — and does not send a stale
 * {@code Authorization} header — on the two calls that must work without one.
 */
@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Auth", description = "Registration and login; issues the JWT used by every other endpoint")
public class AuthController {

    private final AuthService authService;

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Register a new account",
            description = """
                    Creates a USER account and immediately returns an access token, as if the
                    caller had also logged in — there is no `role` field: granting ADMIN is
                    an administrative action performed afterwards through
                    `POST /api/v1/users` by an existing admin.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created and token issued",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponseDto.class),
                            examples = @ExampleObject(name = "New session", value = ApiExamples.AUTH_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "Payload failed validation, or the username/email is already taken",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = {
                                    @ExampleObject(name = "Validation error", value = ApiExamples.PROBLEM_VALIDATION),
                                    @ExampleObject(name = "Duplicate username", value = ApiExamples.PROBLEM_DUPLICATE_USERNAME)
                            }))
    })
    public ResponseEntity<AuthResponseDto> register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "New account details",
                    content = @Content(examples = @ExampleObject(name = "New account", value = ApiExamples.AUTH_REGISTER_REQUEST)))
            @Valid @RequestBody RegisterRequestDto request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Exchange credentials for an access token",
            description = """
                    Verifies username and password and returns a bearer token. Send it back
                    on every other request as `Authorization: Bearer <accessToken>`.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthResponseDto.class),
                            examples = @ExampleObject(name = "Session", value = ApiExamples.AUTH_RESPONSE))),
            @ApiResponse(responseCode = "400", description = "Payload failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Validation error", value = ApiExamples.PROBLEM_VALIDATION))),
            @ApiResponse(responseCode = "401", description = "Username does not exist or password is wrong",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Invalid credentials", value = ApiExamples.PROBLEM_INVALID_CREDENTIALS)))
    })
    public ResponseEntity<AuthResponseDto> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Username and password",
                    content = @Content(examples = @ExampleObject(name = "Credentials", value = ApiExamples.AUTH_LOGIN_REQUEST)))
            @Valid @RequestBody LoginRequestDto request) {

        return ResponseEntity.ok(authService.login(request));
    }
}
