package com.taskmanagement.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Single place where every exception becomes an HTTP response, expressed as an
 * RFC 7807 {@code application/problem+json} document.
 *
 * <p>Design decisions:
 * <ul>
 *   <li><b>Why {@link ProblemDetail} and not a bespoke error record.</b> It is
 *       the standard media type ({@code application/problem+json}), it is what
 *       Spring 6 already produces for framework-level failures, and clients get
 *       one shape for <em>all</em> errors instead of one shape for ours and
 *       another for Spring's.</li>
 *   <li><b>Why extend {@link ResponseEntityExceptionHandler}.</b> It already maps
 *       the ~15 Spring MVC exceptions (unsupported media type, missing
 *       parameter, unreadable body, unknown route, …) to sensible statuses. We
 *       inherit those and override only where we can add information, rather
 *       than re-deriving them and getting the status codes subtly wrong.</li>
 *   <li><b>Stable {@code type} URIs.</b> The status code alone is too coarse to
 *       branch on — three different 400s mean three different client fixes. The
 *       {@code type} URI is the machine-readable discriminator and is treated as
 *       part of the API contract.</li>
 *   <li><b>The catch-all never echoes the exception.</b> Stack traces and
 *       messages from unexpected failures are logged server-side and replaced by
 *       a generic sentence, so internal details are not handed to callers.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Namespace for problem types. These URIs identify a problem category; they
     * are stable identifiers first and documentation links second.
     */
    private static final String PROBLEM_BASE = "https://api.taskmanagement.com/problems/";

    static final URI TYPE_NOT_FOUND = URI.create(PROBLEM_BASE + "resource-not-found");
    static final URI TYPE_VALIDATION = URI.create(PROBLEM_BASE + "validation-error");
    static final URI TYPE_BAD_REQUEST = URI.create(PROBLEM_BASE + "bad-request");
    static final URI TYPE_CONFLICT = URI.create(PROBLEM_BASE + "conflict");
    static final URI TYPE_FORBIDDEN = URI.create(PROBLEM_BASE + "access-denied");
    static final URI TYPE_INTERNAL = URI.create(PROBLEM_BASE + "internal-error");
    // Same URI ProblemDetailAuthenticationEntryPoint uses for filter-chain-level
    // failures (missing/invalid token): the two paths must render identically.
    static final URI TYPE_UNAUTHORIZED = URI.create(PROBLEM_BASE + "unauthorized");

    // ---------------------------------------------------------------------
    // Application exceptions
    // ---------------------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(TYPE_NOT_FOUND);
        problem.setTitle("Resource not found");
        // Machine-readable members so clients do not have to parse `detail`.
        problem.setProperty("resource", ex.getResource());
        problem.setProperty("identifier", ex.getIdentifier());
        return decorate(problem, request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(TYPE_BAD_REQUEST);
        problem.setTitle("Invalid request");
        return decorate(problem, request);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(TYPE_CONFLICT);
        problem.setTitle("Conflicting resource state");
        return decorate(problem, request);
    }

    /**
     * Constraint violations raised outside the request body — typically
     * {@code @Validated} method parameters such as path variables.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(
                        lastPathNode(violation.getPropertyPath().toString()),
                        violation.getMessage(),
                        violation.getInvalidValue()))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more request parameters are invalid");
        problem.setType(TYPE_VALIDATION);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return decorate(problem, request);
    }

    /**
     * Raised by Spring Data when a client sorts on a property that does not
     * exist. Without this it would surface as a 500, even though it is squarely
     * a client mistake.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Unknown sort property: '%s'".formatted(ex.getPropertyName()));
        problem.setType(TYPE_BAD_REQUEST);
        problem.setTitle("Invalid request");
        return decorate(problem, request);
    }

    /**
     * Last line of defence for database constraints (unique keys, foreign keys)
     * that the service layer did not pre-check. The root cause is logged but not
     * returned: it would leak table and constraint names.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation on {}", pathOf(request), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The request conflicts with an existing resource or a data constraint");
        problem.setType(TYPE_CONFLICT);
        problem.setTitle("Conflicting resource state");
        return decorate(problem, request);
    }

    /**
     * Covers {@code AuthenticationException} raised from <em>inside</em> a
     * controller/service call — concretely, {@code AuthServiceImpl.login()}
     * invoking {@code AuthenticationManager.authenticate(...)} with bad
     * credentials. This is a different arrival point from
     * {@code ProblemDetailAuthenticationEntryPoint}, which handles rejections
     * made by the security filter chain itself (missing/invalid bearer token)
     * before a request ever reaches a controller; both render the same
     * {@code unauthorized} problem type so callers see one contract either way.
     *
     * <p>Without this handler the exception would fall through to
     * {@link #handleUnexpected}, since {@code AuthenticationException} is a
     * plain {@code Exception} and no more specific handler would match — turning
     * a wrong password into a 500 instead of a 401.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setType(TYPE_UNAUTHORIZED);
        problem.setTitle("Unauthorized");
        return decorate(problem, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
        problem.setType(TYPE_FORBIDDEN);
        problem.setTitle("Access denied");
        return decorate(problem, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception on {}", pathOf(request), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please contact support.");
        problem.setType(TYPE_INTERNAL);
        problem.setTitle("Internal server error");
        return decorate(problem, request);
    }

    // ---------------------------------------------------------------------
    // Overrides of Spring MVC's own handling
    // ---------------------------------------------------------------------

    /**
     * {@code @Valid} failures on a request body. Spring's default detail is just
     * "Invalid request content."; we keep its status and add the per-field
     * breakdown that a client actually needs to fix the call.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage(), error.getRejectedValue()))
                .toList();

        // Object-level errors (class-level constraints) have no field to point at.
        List<String> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(error -> error.getDefaultMessage())
                .toList();

        ProblemDetail problem = ex.getBody();
        problem.setType(TYPE_VALIDATION);
        problem.setTitle("Validation failed");
        problem.setDetail("The request body failed validation. See 'errors' for details.");
        problem.setProperty("errors", fieldErrors);
        if (!globalErrors.isEmpty()) {
            problem.setProperty("globalErrors", globalErrors);
        }

        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    /**
     * Type conversion failures on query parameters and path variables — most
     * often an invalid enum constant such as {@code ?status=FINISHED}. Listing
     * the accepted values turns a dead end into a self-correcting error.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
                                                        HttpHeaders headers,
                                                        HttpStatusCode status,
                                                        WebRequest request) {
        // Note: only the MethodArgumentTypeMismatchException subclass carries a
        // ProblemDetail of its own, so the body is built here and handed to
        // handleExceptionInternal, which covers both cases uniformly.
        String parameterName = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ex.getPropertyName();
        Class<?> requiredType = ex.getRequiredType();

        String detail;
        List<Object> acceptedValues = null;
        if (requiredType != null && requiredType.isEnum()) {
            Object[] constants = requiredType.getEnumConstants();
            String accepted = Arrays.stream(constants)
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            detail = "'%s' is not a valid value for '%s'. Accepted values: %s."
                    .formatted(ex.getValue(), parameterName, accepted);
            acceptedValues = List.of(constants);
        } else {
            detail = "'%s' is not a valid value for '%s'.".formatted(ex.getValue(), parameterName);
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(TYPE_BAD_REQUEST);
        problem.setTitle("Invalid request");
        problem.setProperty("parameter", parameterName);
        if (acceptedValues != null) {
            problem.setProperty("acceptedValues", acceptedValues);
        }

        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Central hook: every response produced by the inherited framework handlers
     * passes through here, so the {@code instance} and {@code timestamp} members
     * are applied uniformly instead of being repeated in each override.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            decorate(problem, request);
        }
        return response;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Adds the members that every problem document in this API carries:
     * {@code instance} (the URI that failed) and a {@code timestamp}, which is
     * what makes a report correlatable with server logs.
     */
    private ProblemDetail decorate(ProblemDetail problem, WebRequest request) {
        if (problem.getInstance() == null) {
            String path = pathOf(request);
            if (path != null) {
                problem.setInstance(URI.create(path));
            }
        }
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
    }

    /** {@code createTask.request.title} -> {@code title}. */
    private String lastPathNode(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }
}
