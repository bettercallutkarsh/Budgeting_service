package com.budgetingapp.controller;

import com.budgetingapp.dto.request.MonthlyBudgetSaveRequest;
import com.budgetingapp.dto.response.MonthlyBudgetResponse;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.MonthlyBudgetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final MonthlyBudgetService monthlyBudgetService;
    private final AuthService authService;

    public BudgetController(MonthlyBudgetService monthlyBudgetService, AuthService authService) {
        this.monthlyBudgetService = monthlyBudgetService;
        this.authService = authService;
    }

    @GetMapping("/{month}")
    public ResponseEntity<MonthlyBudgetResponse> getBudget(@PathVariable("month") String month) {
        YearMonth parsed = parseMonth(month);
        return monthlyBudgetService.getBudget(authService.currentUserId(), parsed)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "BUDGET_NOT_SET",
                        "No budget is set for " + parsed + "."));
    }

    @PutMapping("/{month}")
    public MonthlyBudgetResponse saveBudget(@PathVariable("month") String month,
                                            @Valid @RequestBody MonthlyBudgetSaveRequest request) {
        return monthlyBudgetService.saveBudget(authService.currentUserId(), parseMonth(month), request);
    }

    private YearMonth parseMonth(String raw) {
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MONTH_FORMAT_INVALID",
                    "month must use the yyyy-MM format.");
        }
    }
}
