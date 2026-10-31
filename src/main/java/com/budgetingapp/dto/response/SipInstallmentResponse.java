package com.budgetingapp.dto.response;

import com.budgetingapp.entity.enums.SipInstallmentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class SipInstallmentResponse {

    private Long sipPlanId;

    private String planName;

    private String period;

    private LocalDate dueDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal amount;

    private SipInstallmentStatus status;

    private Long paidMoneyEntryId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant paidAtUtc;

    private LocalDate paidDate;

    public Long getSipPlanId() {
        return sipPlanId;
    }

    public void setSipPlanId(Long sipPlanId) {
        this.sipPlanId = sipPlanId;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public SipInstallmentStatus getStatus() {
        return status;
    }

    public void setStatus(SipInstallmentStatus status) {
        this.status = status;
    }

    public Long getPaidMoneyEntryId() {
        return paidMoneyEntryId;
    }

    public void setPaidMoneyEntryId(Long paidMoneyEntryId) {
        this.paidMoneyEntryId = paidMoneyEntryId;
    }

    public Instant getPaidAtUtc() {
        return paidAtUtc;
    }

    public void setPaidAtUtc(Instant paidAtUtc) {
        this.paidAtUtc = paidAtUtc;
    }

    public LocalDate getPaidDate() {
        return paidDate;
    }

    public void setPaidDate(LocalDate paidDate) {
        this.paidDate = paidDate;
    }
}
