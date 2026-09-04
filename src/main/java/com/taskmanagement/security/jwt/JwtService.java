package com.taskmanagement.security.jwt;

import com.taskmanagement.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies the JWTs used for stateless authentication.
 *
 * <p>Design decisions:
 * <ul>
 *   <li><b>HS256, one shared secret.</b> This service is both the issuer and the
 *       only verifier of its own tokens, so there is no need for asymmetric keys
 *       (RS256) — those earn their complexity when a *different* service has to
 *       verify tokens it did not issue.</li>
 *   <li><b>The token carries {@code role} but the filter re-derives authorities
 *       from the database on every request</b> (see {@code JwtAuthenticationFilter}).
 *       The claim is kept anyway: it is useful for clients that want to render
 *       UI without an extra call, and it costs nothing to include.</li>
 *   <li><b>{@code issuer} is required on verification.</b> A minor hardening: it
 *       stops a token minted by some other service that happens to reuse the
 *       same secret from being accepted here.</li>
 * </ul>
 */
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final Duration expiration;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.expiration = Duration.ofMinutes(properties.expirationMinutes());
        this.issuer = properties.issuer();
    }

    /** Builds a signed, time-boxed token identifying {@code username}. */
    public String generateToken(String username, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies the signature, issuer and expiry, and returns the claims.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException if the token's {@code exp} has passed
     * @throws JwtException                        for any other structural or signature failure
     * @throws IllegalArgumentException             if {@code token} is null, empty or blank
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Token lifetime in seconds, echoed to clients in the login/register response. */
    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
