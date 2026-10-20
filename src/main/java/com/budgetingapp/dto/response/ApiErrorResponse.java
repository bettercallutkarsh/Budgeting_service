package com.budgetingapp.dto.response;

import com.budgetingapp.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

public class ApiErrorResponse {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private int status;

    private String code;

    private String message;

    private List<BusinessException.FieldViolation> fieldErrors;

    private String traceId;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(Instant timestamp, int status, String code, String message,
                            List<BusinessException.FieldViolation> fieldErrors, String traceId) {
        this.timestamp = timestamp;
        this.status = status;
        this.code = code;
        this.message = message;
        this.fieldErrors = fieldErrors;
        this.traceId = traceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<BusinessException.FieldViolation> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(List<BusinessException.FieldViolation> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
