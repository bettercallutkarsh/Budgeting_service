package com.budgetingapp.exception;

import org.springframework.http.HttpStatus;

public class InvestmentToggleBlockedException extends BusinessException {

    public InvestmentToggleBlockedException() {
        super(HttpStatus.CONFLICT, "INVESTMENT_TOGGLE_BLOCKED",
                "Investments cannot be turned off because this month already has posted investment entries.");
    }
}
