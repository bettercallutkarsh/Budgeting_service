package com.budgetingapp.dto.response;

import com.budgetingapp.entity.enums.AllocationInputMode;
import com.budgetingapp.entity.enums.AllocationMode;
import com.budgetingapp.entity.enums.IncomeType;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;

public class MonthlyBudgetResponse {

    private String month;

    private IncomeType incomeType;

    private String incomeSourceLabel;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal incomeAmount;

    private AllocationMode allocationMode;

    private AllocationInputMode allocationInputMode;

    private Boolean investmentsEnabled;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal essentialsPercentage;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal safetyPercentage;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal investmentsPercentage;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal essentialsAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal safetyAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal investmentsAmount;

    private Instant createdAtUtc;

    private Instant updatedAtUtc;

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

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

    public Instant getCreatedAtUtc() {
        return createdAtUtc;
    }

    public void setCreatedAtUtc(Instant createdAtUtc) {
        this.createdAtUtc = createdAtUtc;
    }

    public Instant getUpdatedAtUtc() {
        return updatedAtUtc;
    }

    public void setUpdatedAtUtc(Instant updatedAtUtc) {
        this.updatedAtUtc = updatedAtUtc;
    }
}
