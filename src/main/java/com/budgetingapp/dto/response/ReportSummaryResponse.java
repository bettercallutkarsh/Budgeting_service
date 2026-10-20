package com.budgetingapp.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ReportSummaryResponse {

    public static class Totals {

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal consumptionExpenditure;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal safetySavings;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal investmentContributions;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal recordedIncome;

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
    }

    private LocalDate fromDate;

    private LocalDate toDate;

    private String groupBy;

    private Boolean incomeIncluded;

    private String explanation;

    private List<ReportPeriodResponse> periods;

    private Totals totals;

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public String getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }

    public Boolean getIncomeIncluded() {
        return incomeIncluded;
    }

    public void setIncomeIncluded(Boolean incomeIncluded) {
        this.incomeIncluded = incomeIncluded;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<ReportPeriodResponse> getPeriods() {
        return periods;
    }

    public void setPeriods(List<ReportPeriodResponse> periods) {
        this.periods = periods;
    }

    public Totals getTotals() {
        return totals;
    }

    public void setTotals(Totals totals) {
        this.totals = totals;
    }
}
