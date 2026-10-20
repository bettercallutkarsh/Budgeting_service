package com.budgetingapp.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class AllocationValidationException extends BusinessException {

    public AllocationValidationException(String code, String message, List<FieldViolation> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, code, message, fieldErrors);
    }

    public static AllocationValidationException of(String code, String message, String field, String fieldCode) {
        return new AllocationValidationException(code, message,
                List.of(new FieldViolation(field, fieldCode, message)));
    }
}
