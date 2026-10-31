package com.budgetingapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SipPlanCreateRequest {

    @NotBlank(message = "planName is required")
    @Size(max = 120, message = "planName must be at most 120 characters")
    private String planName;

    @NotNull(message = "monthlyAmount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "monthlyAmount must be greater than 0.00")
    @Digits(integer = 12, fraction = 2, message = "monthlyAmount must have at most 2 decimal places")
    private BigDecimal monthlyAmount;

    @NotNull(message = "installmentDay is required")
    @Min(value = 1, message = "installmentDay must be between 1 and 31")
    @Max(value = 31, message = "installmentDay must be between 1 and 31")
    private Integer installmentDay;

    @NotNull(message = "originalStartDate is required")
    private LocalDate originalStartDate;

    @NotNull(message = "trackingStartDate is required")
    private LocalDate trackingStartDate;

    private LocalDate plannedEndDate;

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public BigDecimal getMonthlyAmount() {
        return monthlyAmount;
    }

    public void setMonthlyAmount(BigDecimal monthlyAmount) {
        this.monthlyAmount = monthlyAmount;
    }

    public Integer getInstallmentDay() {
        return installmentDay;
    }

    public void setInstallmentDay(Integer installmentDay) {
        this.installmentDay = installmentDay;
    }

    public LocalDate getOriginalStartDate() {
        return originalStartDate;
    }

    public void setOriginalStartDate(LocalDate originalStartDate) {
        this.originalStartDate = originalStartDate;
    }

    public LocalDate getTrackingStartDate() {
        return trackingStartDate;
    }

    public void setTrackingStartDate(LocalDate trackingStartDate) {
        this.trackingStartDate = trackingStartDate;
    }

    public LocalDate getPlannedEndDate() {
        return plannedEndDate;
    }

    public void setPlannedEndDate(LocalDate plannedEndDate) {
        this.plannedEndDate = plannedEndDate;
    }
}
