package com.budgetingapp.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public class ReportPeriodResponse {

    private String period;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal consumptionExpenditure;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal safetySavings;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal investmentContributions;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal recordedIncome;

    private Boolean incomeIncluded;

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
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

    public BigDecimal getRecordedIncome() {
        return recordedIncome;
    }

    public void setRecordedIncome(BigDecimal recordedIncome) {
        this.recordedIncome = recordedIncome;
    }

    public Boolean getIncomeIncluded() {
        return incomeIncluded;
    }

    public void setIncomeIncluded(Boolean incomeIncluded) {
        this.incomeIncluded = incomeIncluded;
    }
}
