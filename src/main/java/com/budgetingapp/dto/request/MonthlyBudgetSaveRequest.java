package com.budgetingapp.dto.request;

import com.budgetingapp.entity.enums.AllocationInputMode;
import com.budgetingapp.entity.enums.AllocationMode;
import com.budgetingapp.entity.enums.IncomeType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class MonthlyBudgetSaveRequest {

    @NotNull(message = "incomeType is required")
    private IncomeType incomeType;

    @Size(max = 120, message = "incomeSourceLabel must be at most 120 characters")
    private String incomeSourceLabel;

    @NotNull(message = "incomeAmount is required")
    @DecimalMin(value = "0.00", message = "incomeAmount must not be negative")
    @Digits(integer = 12, fraction = 2, message = "incomeAmount must have at most 2 decimal places")
    private BigDecimal incomeAmount;

    @NotNull(message = "allocationMode is required")
    private AllocationMode allocationMode;

    private AllocationInputMode allocationInputMode;

    @NotNull(message = "investmentsEnabled is required")
    private Boolean investmentsEnabled;

    @DecimalMin(value = "0.00", message = "essentialsPercentage must not be negative")
    @DecimalMax(value = "100.00", message = "essentialsPercentage must not exceed 100.00")
    @Digits(integer = 3, fraction = 2, message = "essentialsPercentage must have at most 2 decimal places")
    private BigDecimal essentialsPercentage;

    @DecimalMin(value = "0.00", message = "safetyPercentage must not be negative")
    @DecimalMax(value = "100.00", message = "safetyPercentage must not exceed 100.00")
    @Digits(integer = 3, fraction = 2, message = "safetyPercentage must have at most 2 decimal places")
    private BigDecimal safetyPercentage;

    @DecimalMin(value = "0.00", message = "investmentsPercentage must not be negative")
    @DecimalMax(value = "100.00", message = "investmentsPercentage must not exceed 100.00")
    @Digits(integer = 3, fraction = 2, message = "investmentsPercentage must have at most 2 decimal places")
    private BigDecimal investmentsPercentage;

    @DecimalMin(value = "0.00", message = "essentialsAmount must not be negative")
    @Digits(integer = 12, fraction = 2, message = "essentialsAmount must have at most 2 decimal places")
    private BigDecimal essentialsAmount;

    @DecimalMin(value = "0.00", message = "safetyAmount must not be negative")
    @Digits(integer = 12, fraction = 2, message = "safetyAmount must have at most 2 decimal places")
    private BigDecimal safetyAmount;

    @DecimalMin(value = "0.00", message = "investmentsAmount must not be negative")
    @Digits(integer = 12, fraction = 2, message = "investmentsAmount must have at most 2 decimal places")
    private BigDecimal investmentsAmount;

    public IncomeType getIncomeType() {
        return incomeType;
    }

    public void setIncomeType(IncomeType incomeType) {
        this.incomeType = incomeType;
    }

    public String getIncomeSourceLabel() {
        return incomeSourceLabel;
    }

    public void setIncomeSourceLabel(String incomeSourceLabel) {
        this.incomeSourceLabel = incomeSourceLabel;
    }

    public BigDecimal getIncomeAmount() {
        return incomeAmount;
    }

    public void setIncomeAmount(BigDecimal incomeAmount) {
        this.incomeAmount = incomeAmount;
    }

    public AllocationMode getAllocationMode() {
        return allocationMode;
    }

    public void setAllocationMode(AllocationMode allocationMode) {
        this.allocationMode = allocationMode;
    }

    public AllocationInputMode getAllocationInputMode() {
        return allocationInputMode;
    }

    public void setAllocationInputMode(AllocationInputMode allocationInputMode) {
        this.allocationInputMode = allocationInputMode;
    }

    public Boolean getInvestmentsEnabled() {
        return investmentsEnabled;
    }

    public void setInvestmentsEnabled(Boolean investmentsEnabled) {
        this.investmentsEnabled = investmentsEnabled;
    }

    public BigDecimal getEssentialsPercentage() {
        return essentialsPercentage;
    }

    public void setEssentialsPercentage(BigDecimal essentialsPercentage) {
        this.essentialsPercentage = essentialsPercentage;
    }

    public BigDecimal getSafetyPercentage() {
        return safetyPercentage;
    }

    public void setSafetyPercentage(BigDecimal safetyPercentage) {
        this.safetyPercentage = safetyPercentage;
    }

    public BigDecimal getInvestmentsPercentage() {
        return investmentsPercentage;
    }

    public void setInvestmentsPercentage(BigDecimal investmentsPercentage) {
        this.investmentsPercentage = investmentsPercentage;
    }

    public BigDecimal getEssentialsAmount() {
        return essentialsAmount;
    }

    public void setEssentialsAmount(BigDecimal essentialsAmount) {
        this.essentialsAmount = essentialsAmount;
    }

    public BigDecimal getSafetyAmount() {
        return safetyAmount;
    }

    public void setSafetyAmount(BigDecimal safetyAmount) {
        this.safetyAmount = safetyAmount;
    }

    public BigDecimal getInvestmentsAmount() {
        return investmentsAmount;
    }

    public void setInvestmentsAmount(BigDecimal investmentsAmount) {
        this.investmentsAmount = investmentsAmount;
    }
}
