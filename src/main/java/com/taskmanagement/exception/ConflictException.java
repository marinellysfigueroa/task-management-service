package com.taskmanagement.exception;

/**
 * Thrown when a request is well-formed but cannot be applied against the current
 * state of the resource — for example deleting a project that still owns tasks.
 * Mapped to HTTP 409.
 *
 * <p>Kept separate from {@link BadRequestException} because the two carry
 * genuinely different information for the caller: a 400 says "fix your payload",
 * a 409 says "the payload is fine, the server state is not what you assumed".
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
