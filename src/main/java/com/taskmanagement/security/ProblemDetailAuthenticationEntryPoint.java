package com.taskmanagement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

/**
 * Renders authentication failures as RFC 7807 documents.
 *
 * <p>Design decision: {@code @RestControllerAdvice} only sees exceptions raised
 * once a request has reached a controller. Authentication is rejected earlier,
 * in the security filter chain, so without this component a caller with a
 * missing or bad token would get Spring Security's default empty 401 — a
 * different error contract from every other failure in the API. Wiring the
 * entry point (and its {@link ProblemDetailAccessDeniedHandler} counterpart) is
 * what makes {@code application/problem+json} genuinely uniform.
 *
 * <p>This is also the single sink both {@code JwtAuthenticationFilter} (bad
 * token) and Spring Security's own {@code AuthorizationFilter} (no token at
 * all, on a protected route) funnel into, so {@code authException.getMessage()}
 * carries whichever detail applies — "expired", "invalid", "account no longer
 * exists", or the framework's own generic message when no token was sent — and
 * is trusted as-is rather than replaced with one fixed sentence.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String DEFAULT_DETAIL = "Authentication is required to access this resource";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        String detail = authException.getMessage() != null ? authException.getMessage() : DEFAULT_DETAIL;

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setType(URI.create("https://api.taskmanagement.com/problems/unauthorized"));
        problem.setTitle("Unauthorized");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());

        // The challenge header names the scheme clients should retry with; kept
        // for standards compliance even though few HTTP clients act on it.
        response.setHeader("WWW-Authenticate", "Bearer realm=\"task-management-service\"");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
