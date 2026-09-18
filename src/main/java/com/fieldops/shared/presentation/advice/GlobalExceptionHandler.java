package com.fieldops.shared.presentation.advice;

import com.fieldops.auth.domain.exception.AuthException;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.infrastructure.filter.RequestIdFilter;
import com.fieldops.shared.presentation.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Global exception handler that translates every exception into the
 * canonical {@link ApiError} envelope.
 *
 * <p><strong>Golden rule:</strong> no stack trace, SQL fragment or internal
 * class name ever reaches the client. Technical detail is logged with the
 * correlated {@code requestId}.</p>
 *
 * <p>Authentication filter failures are rendered directly by the security
 * entry point; controller-level authentication failures use the handler below.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 400 Validation ──────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ApiError body = buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "One or more fields failed validation", request, fieldErrors);

        log.warn("Validation failed on {} {}: {}", request.getMethod(), request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleMalformedRequest(Exception ex,
                                                           HttpServletRequest request) {
        ApiError body = buildError(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The request contains an invalid value",
                request
        );
        log.warn("Malformed request on {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException ex,
                                                         HttpServletRequest request) {
        ApiError body = buildError(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request);
        log.warn("Invalid request on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                           HttpServletRequest request) {
        ApiError body = buildError(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "The requested HTTP method is not supported for this resource",
                request
        );
        log.warn("Unsupported HTTP method {} on {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    // ── 404 Not Found ───────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex,
                                                   HttpServletRequest request) {
        ApiError body = buildError(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), request);
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ── 409 Conflict ────────────────────────────────────────────────────

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex,
                                                   HttpServletRequest request) {
        ApiError body = buildError(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request);
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // ── 422 Business Rule ───────────────────────────────────────────────

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex,
                                                      HttpServletRequest request) {
        ApiError body = buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage(), request);
        log.warn("Business rule violated: {} — {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthException ex,
                                                         HttpServletRequest request) {
        ApiError body = buildError(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage(), request);
        log.warn("Authentication rejected: {}", ex.getCode());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        ApiError body = buildError(
                HttpStatus.FORBIDDEN,
                "AUTH_FORBIDDEN",
                "You do not have permission to access this resource",
                request
        );
        log.warn("Authorization rejected for {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // ── 500 Fallback ────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex,
                                                     HttpServletRequest request) {
        ApiError body = buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Use the requestId for support.", request);
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private ApiError buildError(HttpStatus status, String code, String message,
                                HttpServletRequest request) {
        return buildError(status, code, message, request, List.of());
    }

    private ApiError buildError(HttpStatus status, String code, String message,
                                HttpServletRequest request, List<ApiError.FieldError> fieldErrors) {
        return new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY),
                fieldErrors
        );
    }
}
