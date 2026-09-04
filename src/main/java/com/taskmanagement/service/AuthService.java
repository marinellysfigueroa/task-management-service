package com.taskmanagement.service;

import com.taskmanagement.dto.AuthResponseDto;
import com.taskmanagement.dto.LoginRequestDto;
import com.taskmanagement.dto.RegisterRequestDto;

/**
 * Use cases for account creation and credential exchange.
 *
 * <p>Kept separate from {@link UserService}: registration is a public,
 * self-service action that always produces a {@code USER} account, whereas
 * {@code UserService} backs the ADMIN-only user management endpoints, which can
 * create a user with any role. Sharing one DTO/method between the two would
 * mean re-deriving "is this call allowed to set an arbitrary role" from
 * context; two distinct entry points make that a compile-time-obvious
 * boundary instead.
 */
public interface AuthService {

    /** Creates a USER account and immediately issues it a token, as if it had also logged in. */
    AuthResponseDto register(RegisterRequestDto request);

    /** Verifies credentials and issues an access token. */
    AuthResponseDto login(LoginRequestDto request);
}
