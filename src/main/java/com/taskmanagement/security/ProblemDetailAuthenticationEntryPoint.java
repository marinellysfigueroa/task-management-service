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
 * in the security filter chain, so without this component a caller with bad
 * credentials would get Spring Security's default empty 401 — a different error
 * contract from every other failure in the API. Wiring the entry point (and its
 * {@link ProblemDetailAccessDeniedHandler} counterpart) is what makes
 * {@code application/problem+json} genuinely uniform.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource");
        problem.setType(URI.create("https://api.taskmanagement.com/problems/unauthorized"));
        problem.setTitle("Unauthorized");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());

        // The challenge header is part of the HTTP Basic contract and is kept so
        // that standard clients know how to authenticate.
        response.setHeader("WWW-Authenticate", "Basic realm=\"task-management-service\"");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
