package com.taskmanagement.security.jwt;

import com.taskmanagement.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests for the token crypto itself — no Spring context needed,
 * so these run in milliseconds and pin down the contract the filter and
 * {@code AuthServiceImpl} depend on.
 */
class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "a-256-bit-secret-used-only-by-this-test-class!!".getBytes()); // 48 bytes, well over the 32-byte HS256 minimum

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60, "test-issuer"));

    @Test
    void shouldRoundTripSubjectAndRoleThroughTheToken() {
        String token = jwtService.generateToken("alice", Role.ADMIN);

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.getIssuer()).isEqualTo("test-issuer");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void shouldExposeTheConfiguredExpirationInSeconds() {
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(TimeUnit.MINUTES.toSeconds(60));
    }

    @Test
    void shouldRejectATokenSignedWithADifferentSecret() {
        String otherSecret = Base64.getEncoder().encodeToString("a-completely-different-32-byte-secret!!".getBytes());
        JwtService otherService = new JwtService(new JwtProperties(otherSecret, 60, "test-issuer"));
        String tokenFromOtherService = otherService.generateToken("alice", Role.USER);

        assertThatThrownBy(() -> jwtService.parseClaims(tokenFromOtherService))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void shouldRejectAStructurallyBrokenToken() {
        assertThatThrownBy(() -> jwtService.parseClaims("not-a-jwt-at-all"))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void shouldRejectATokenIssuedByAnUnexpectedIssuer() {
        JwtService otherIssuer = new JwtService(new JwtProperties(SECRET, 60, "someone-elses-service"));
        String token = otherIssuer.generateToken("alice", Role.USER);

        // Same secret, different issuer: this is exactly the case the required
        // issuer check exists to catch — a token that verifies cryptographically
        // but was never meant for this service.
        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void shouldRejectAnExpiredToken() {
        JwtService shortLived = new JwtService(new JwtProperties(SECRET, 0, "test-issuer"));
        String token = shortLived.generateToken("alice", Role.USER);

        // expirationMinutes=0 means issuedAt == expiration, so the token is
        // already expired the instant it is parsed back.
        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
