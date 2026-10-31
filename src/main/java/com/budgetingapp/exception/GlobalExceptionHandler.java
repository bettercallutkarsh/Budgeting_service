package com.budgetingapp.exception;

import com.budgetingapp.dto.response.ApiErrorResponse;
import com.budgetingapp.logging.LogSanitizer;
import com.budgetingapp.logging.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

import java.sql.SQLException;
import java.time.Clock;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex,
                                                                    HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        if (status.is5xxServerError()) {
            log.error("business.failure status={} errorCode={} method={} route={}",
                    status.value(), ex.getCode(), methodOf(request), routeOf(request), ex);
        } else if (isOperationallySignificant(status)) {
            log.warn("business.rejected status={} errorCode={} method={} route={}",
                    status.value(), ex.getCode(), methodOf(request), routeOf(request));
        } else {
            log.info("business.rejected status={} errorCode={} method={} route={}",
                    status.value(), ex.getCode(), methodOf(request), routeOf(request));
        }
        return build(status, ex.getCode(), ex.getMessage(), ex.getFieldErrors());
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginRateLimited(LoginRateLimitedException ex,
                                                                   HttpServletRequest request) {
        log.warn("security.rate_limited status={} errorCode={} retryAfterSeconds={} method={} route={}",
                ex.getStatus().value(), ex.getCode(), ex.getRetryAfterSeconds(),
                methodOf(request), routeOf(request));
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(responseBody(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getFieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex,
                                                                      HttpServletRequest request) {
        List<BusinessException.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new BusinessException.FieldViolation(
                        fieldError.getField(),
                        fieldError.getCode() == null ? "INVALID" : fieldError.getCode(),
                        fieldError.getDefaultMessage()))
                .toList();
        String fields = violations.stream().map(BusinessException.FieldViolation::field).distinct()
                .reduce((left, right) -> left + "," + right).orElse("-");
        log.info("request.validation_failed status=400 errorCode=REQUEST_INVALID fields={} method={} route={}",
                LogSanitizer.sanitize(fields), methodOf(request), routeOf(request));
        return build(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Request validation failed.", violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        log.info("request.malformed status=400 errorCode=REQUEST_MALFORMED method={} route={}",
                methodOf(request), routeOf(request));
        return build(HttpStatus.BAD_REQUEST, "REQUEST_MALFORMED", "Request body is malformed or unreadable.", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        String field = ex.getName();
        List<BusinessException.FieldViolation> violations = List.of(new BusinessException.FieldViolation(
                field, "VALUE_INVALID",
                "Parameter " + field + " has an invalid value."));
        log.info("request.invalid_parameter status=400 errorCode=REQUEST_INVALID field={} method={} route={}",
                LogSanitizer.sanitize(field), methodOf(request), routeOf(request));
        return build(HttpStatus.BAD_REQUEST, "REQUEST_INVALID",
                "Parameter " + field + " has an invalid value.", violations);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDatabaseFailure(DataAccessException ex,
                                                                  HttpServletRequest request) {
        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(ex);
        String sqlState = null;
        Integer vendorCode = null;
        if (rootCause instanceof SQLException sqlException) {
            sqlState = sqlException.getSQLState();
            vendorCode = sqlException.getErrorCode();
        }
        log.error("db.failure exceptionClass={} sqlState={} vendorCode={} method={} route={}",
                ex.getClass().getName(), LogSanitizer.sanitize(sqlState),
                vendorCode != null ? vendorCode.toString() : "-", methodOf(request), routeOf(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("request.failed status=500 errorCode=INTERNAL_ERROR exceptionClass={} method={} route={}",
                ex.getClass().getName(), methodOf(request), routeOf(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", List.of());
    }

    private boolean isOperationallySignificant(HttpStatus status) {
        return status == HttpStatus.UNAUTHORIZED
                || status == HttpStatus.FORBIDDEN
                || status == HttpStatus.CONFLICT
                || status == HttpStatus.TOO_MANY_REQUESTS;
    }

    private String methodOf(HttpServletRequest request) {
        return LogSanitizer.sanitize(request.getMethod());
    }

    private String routeOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern != null ? pattern.toString() : request.getRequestURI();
        return LogSanitizer.sanitize(route);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message,
                                                   List<BusinessException.FieldViolation> fieldErrors) {
        return ResponseEntity.status(status).body(responseBody(status, code, message, fieldErrors));
    }

    private ApiErrorResponse responseBody(HttpStatus status, String code, String message,
                                          List<BusinessException.FieldViolation> fieldErrors) {
        return new ApiErrorResponse(clock.instant(), status.value(), code, message, fieldErrors,
                RequestIds.currentOrNew());
    }
}
