package com.taskmanagement.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.jwt.*} configuration keys.
 *
 * <p>{@code secret} has no default in the {@code aws} profile (see
 * {@code application-aws.yml}), so a production deployment started without
 * {@code JWT_SECRET} set fails at startup rather than silently signing tokens
 * with a key an attacker can read straight out of this repository.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMinutes, String issuer) {
}
