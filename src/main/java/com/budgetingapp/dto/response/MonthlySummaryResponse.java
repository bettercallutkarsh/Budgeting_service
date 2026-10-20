package com.budgetingapp.dto.response;

import com.budgetingapp.entity.enums.AllocationMode;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public class MonthlySummaryResponse {

    public enum MonthStatus {
        BUDGET_NOT_SET,
        NO_INCOME,
        WITHIN_ALLOCATION,
        OVER_ALLOCATION
    }

    private String month;

    private MonthStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal incomeAmount;

    private AllocationMode allocationMode;

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

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal essentialsUsed;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal safetyUsed;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal investmentsUsed;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal essentialsRemaining;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal safetyRemaining;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal investmentsRemaining;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal consumptionExpenditure;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal safetySavings;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal investmentContributions;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal creditCardOutstandingAmount;

    private String explanation;

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public MonthStatus getStatus() {
        return status;
    }

    public void setStatus(MonthStatus status) {
        this.status = status;
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

    public BigDecimal getEssentialsUsed() {
        return essentialsUsed;
    }

    public void setEssentialsUsed(BigDecimal essentialsUsed) {
        this.essentialsUsed = essentialsUsed;
    }

    public BigDecimal getSafetyUsed() {
        return safetyUsed;
    }

    public void setSafetyUsed(BigDecimal safetyUsed) {
        this.safetyUsed = safetyUsed;
    }

    public BigDecimal getInvestmentsUsed() {
        return investmentsUsed;
    }

    public void setInvestmentsUsed(BigDecimal investmentsUsed) {
        this.investmentsUsed = investmentsUsed;
    }

    public BigDecimal getEssentialsRemaining() {
        return essentialsRemaining;
    }

    public void setEssentialsRemaining(BigDecimal essentialsRemaining) {
        this.essentialsRemaining = essentialsRemaining;
    }

    public BigDecimal getSafetyRemaining() {
        return safetyRemaining;
    }

    public void setSafetyRemaining(BigDecimal safetyRemaining) {
        this.safetyRemaining = safetyRemaining;
    }

    public BigDecimal getInvestmentsRemaining() {
        return investmentsRemaining;
    }

    public void setInvestmentsRemaining(BigDecimal investmentsRemaining) {
        this.investmentsRemaining = investmentsRemaining;
    }

    public BigDecimal getConsumptionExpenditure() {
        return consumptionExpenditure;
    }

    public void setConsumptionExpenditure(BigDecimal consumptionExpenditure) {
        this.consumptionExpenditure = consumptionExpenditure;
    }

    public BigDecimal getSafetySavings() {
        return safetySavings;
    }

    public void setSafetySavings(BigDecimal safetySavings) {
        this.safetySavings = safetySavings;
    }

    public BigDecimal getInvestmentContributions() {
        return investmentContributions;
    }

    public void setInvestmentContributions(BigDecimal investmentContributions) {
        this.investmentContributions = investmentContributions;
    }

    public BigDecimal getCreditCardOutstandingAmount() {
        return creditCardOutstandingAmount;
    }

    public void setCreditCardOutstandingAmount(BigDecimal creditCardOutstandingAmount) {
        this.creditCardOutstandingAmount = creditCardOutstandingAmount;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}
