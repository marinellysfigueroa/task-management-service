package com.taskmanagement.controller;

import com.taskmanagement.dto.UserRequestDto;
import com.taskmanagement.dto.UserResponseDto;
import com.taskmanagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administrative user management.
 *
 * <p>Design decision: every endpoint here requires ADMIN. Self-service account
 * creation lives at {@code POST /api/v1/auth/register} instead (always creates
 * a USER, no {@code role} field accepted) — this controller is what an admin
 * uses to create accounts with an arbitrary role, promote/demote a user, or
 * remove one, and none of that belongs on a publicly reachable endpoint.
 *
 * <p>{@code @PreAuthorize} is used here rather than a URL rule in
 * {@code SecurityConfig}: the authorization decision then sits next to the code
 * it protects instead of in a separate file the reader has to cross-reference.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "User management endpoints (ADMIN only)")
public class UserController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create a new user", description = "Requires the ADMIN role. For self-service account creation, use POST /api/v1/auth/register instead.")
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody UserRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id", description = "Requires the ADMIN role.")
    public ResponseEntity<UserResponseDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping
    @Operation(summary = "List all users", description = "Requires the ADMIN role.")
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing user", description = "Requires the ADMIN role.")
    public ResponseEntity<UserResponseDto> updateUser(@PathVariable Long id, @Valid @RequestBody UserRequestDto request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user", description = "Requires the ADMIN role.")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
