package com.taskmanagement.exception;

import lombok.Getter;

/**
 * Thrown when a referenced aggregate does not exist. Mapped to HTTP 404.
 *
 * <p>The resource name and identifier are carried as fields, not just baked into
 * the message, so the exception handler can expose them as machine-readable
 * members of the {@code ProblemDetail} instead of forcing clients to parse a
 * human-readable sentence.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;
    private final transient Object identifier;

    public ResourceNotFoundException(String resource, Object identifier) {
        super("%s not found with id: %s".formatted(resource, identifier));
        this.resource = resource;
        this.identifier = identifier;
    }

    public static ResourceNotFoundException of(String resource, Object identifier) {
        return new ResourceNotFoundException(resource, identifier);
    }
}
