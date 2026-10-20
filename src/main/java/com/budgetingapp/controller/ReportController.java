package com.budgetingapp.controller;

import com.budgetingapp.dto.response.MonthlySummaryResponse;
import com.budgetingapp.dto.response.ReportSummaryResponse;
import com.budgetingapp.exception.BusinessException;
import com.budgetingapp.service.AuthService;
import com.budgetingapp.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final AuthService authService;

    public ReportController(ReportService reportService, AuthService authService) {
        this.reportService = reportService;
        this.authService = authService;
    }

    @GetMapping("/monthly-summary")
    public MonthlySummaryResponse monthlySummary(
            @RequestParam(name = "month", required = false) String month) {
        return reportService.monthlySummary(authService.currentUserId(), parseMonth(month));
    }

    @GetMapping("/summary")
    public ReportSummaryResponse summary(
            @RequestParam(name = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false) LocalDate toDate,
            @RequestParam(name = "groupBy", required = false) String groupBy) {
        return reportService.summary(authService.currentUserId(), fromDate, toDate, groupBy);
    }

    private YearMonth parseMonth(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MONTH_FORMAT_INVALID",
                    "month is required and must use the yyyy-MM format.");
        }
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MONTH_FORMAT_INVALID",
                    "month must use the yyyy-MM format.");
        }
    }
}
