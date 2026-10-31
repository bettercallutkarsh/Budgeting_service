package com.budgetingapp.dto.response;

import com.budgetingapp.entity.enums.SipPlanStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class SipPlanResponse {

    private Long sipPlanId;

    private String investmentType = "SIP";

    private String planName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal monthlyAmount;

    private Integer installmentDay;

    private LocalDate originalStartDate;

    private LocalDate trackingStartDate;

    private LocalDate plannedEndDate;

    private LocalDate actualEndedDate;

    private SipPlanStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAtUtc;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAtUtc;

    public Long getSipPlanId() {
        return sipPlanId;
    }

    public void setSipPlanId(Long sipPlanId) {
        this.sipPlanId = sipPlanId;
    }

    public String getInvestmentType() {
        return investmentType;
    }

    public void setInvestmentType(String investmentType) {
        this.investmentType = investmentType;
    }

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

    public LocalDate getActualEndedDate() {
        return actualEndedDate;
    }

    public void setActualEndedDate(LocalDate actualEndedDate) {
        this.actualEndedDate = actualEndedDate;
    }

    public SipPlanStatus getStatus() {
        return status;
    }

    public void setStatus(SipPlanStatus status) {
        this.status = status;
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
