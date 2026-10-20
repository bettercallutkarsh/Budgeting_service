package com.budgetingapp.exception;

import org.springframework.http.HttpStatus;

public class PeriodReadOnlyException extends BusinessException {

    public PeriodReadOnlyException(String month) {
        this(month, "budget");
    }

    public PeriodReadOnlyException(String month, String subject) {
        super(HttpStatus.CONFLICT, "PERIOD_READ_ONLY",
                "The " + subject + " for " + month + " belongs to a past month and is read-only.");
    }
}
