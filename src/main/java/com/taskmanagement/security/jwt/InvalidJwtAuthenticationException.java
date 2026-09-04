package com.taskmanagement.security.jwt;

import org.springframework.security.core.AuthenticationException;

/**
 * Raised by {@link JwtAuthenticationFilter} for a syntactically-present but
 * unusable bearer token (expired, malformed, bad signature, or naming an
 * account that no longer exists). Kept distinct from "no token supplied at
 * all" so the response can say what is actually wrong instead of the generic
 * "authentication required".
 */
public class InvalidJwtAuthenticationException extends AuthenticationException {

    public InvalidJwtAuthenticationException(String message) {
        super(message);
    }
}
