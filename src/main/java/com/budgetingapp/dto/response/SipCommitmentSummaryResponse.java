package com.budgetingapp.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public class SipCommitmentSummaryResponse {

    private String month;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal investmentsAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal activeMonthlyCommitment;

    private Boolean withinAllocation;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal overAllocationAmount;

    private Integer activePlanCount;

    private String message;

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public BigDecimal getInvestmentsAmount() {
        return investmentsAmount;
    }

    public void setInvestmentsAmount(BigDecimal investmentsAmount) {
        this.investmentsAmount = investmentsAmount;
    }

    public BigDecimal getActiveMonthlyCommitment() {
        return activeMonthlyCommitment;
    }

    public void setActiveMonthlyCommitment(BigDecimal activeMonthlyCommitment) {
        this.activeMonthlyCommitment = activeMonthlyCommitment;
    }

    public Boolean getWithinAllocation() {
        return withinAllocation;
    }

    public void setWithinAllocation(Boolean withinAllocation) {
        this.withinAllocation = withinAllocation;
    }

    public BigDecimal getOverAllocationAmount() {
        return overAllocationAmount;
    }

    public void setOverAllocationAmount(BigDecimal overAllocationAmount) {
        this.overAllocationAmount = overAllocationAmount;
    }

    public Integer getActivePlanCount() {
        return activePlanCount;
    }

    public void setActivePlanCount(Integer activePlanCount) {
        this.activePlanCount = activePlanCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
