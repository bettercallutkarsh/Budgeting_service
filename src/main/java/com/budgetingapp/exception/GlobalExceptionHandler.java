package com.budgetingapp.exception;

import com.budgetingapp.dto.response.ApiErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getFieldErrors());
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginRateLimited(LoginRateLimitedException ex) {
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(responseBody(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getFieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<BusinessException.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new BusinessException.FieldViolation(
                        fieldError.getField(),
                        fieldError.getCode() == null ? "INVALID" : fieldError.getCode(),
                        fieldError.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Request validation failed.", violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "REQUEST_MALFORMED", "Request body is malformed or unreadable.", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String field = ex.getName();
        List<BusinessException.FieldViolation> violations = List.of(new BusinessException.FieldViolation(
                field, "VALUE_INVALID",
                "Parameter " + field + " has an invalid value."));
        return build(HttpStatus.BAD_REQUEST, "REQUEST_INVALID",
                "Parameter " + field + " has an invalid value.", violations);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", List.of());
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message,
                                                   List<BusinessException.FieldViolation> fieldErrors) {
        return ResponseEntity.status(status).body(responseBody(status, code, message, fieldErrors));
    }

    private ApiErrorResponse responseBody(HttpStatus status, String code, String message,
                                          List<BusinessException.FieldViolation> fieldErrors) {
        return new ApiErrorResponse(clock.instant(), status.value(), code, message, fieldErrors,
                UUID.randomUUID().toString());
    }
}
