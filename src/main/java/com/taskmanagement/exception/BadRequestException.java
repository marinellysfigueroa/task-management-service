package com.taskmanagement.exception;

/**
 * Thrown for domain validation failures that are not covered by bean
 * validation annotations, e.g. duplicate usernames/emails.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
