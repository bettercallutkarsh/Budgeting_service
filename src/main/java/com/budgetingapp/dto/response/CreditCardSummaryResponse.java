package com.budgetingapp.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;

public class CreditCardSummaryResponse {

    private Boolean trackingConfigured;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal openingOutstandingAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal currentOutstandingAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalPostedPurchases;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalPostedPayments;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant trackingStartedAtUtc;

    private String explanation;

    public Boolean getTrackingConfigured() {
        return trackingConfigured;
    }

    public void setTrackingConfigured(Boolean trackingConfigured) {
        this.trackingConfigured = trackingConfigured;
    }

    public BigDecimal getOpeningOutstandingAmount() {
        return openingOutstandingAmount;
    }

    public void setOpeningOutstandingAmount(BigDecimal openingOutstandingAmount) {
        this.openingOutstandingAmount = openingOutstandingAmount;
    }

    public BigDecimal getCurrentOutstandingAmount() {
        return currentOutstandingAmount;
    }

    public void setCurrentOutstandingAmount(BigDecimal currentOutstandingAmount) {
        this.currentOutstandingAmount = currentOutstandingAmount;
    }

    public BigDecimal getTotalPostedPurchases() {
        return totalPostedPurchases;
    }

    public void setTotalPostedPurchases(BigDecimal totalPostedPurchases) {
        this.totalPostedPurchases = totalPostedPurchases;
    }

    public BigDecimal getTotalPostedPayments() {
        return totalPostedPayments;
    }

    public void setTotalPostedPayments(BigDecimal totalPostedPayments) {
        this.totalPostedPayments = totalPostedPayments;
    }

    public Instant getTrackingStartedAtUtc() {
        return trackingStartedAtUtc;
    }

    public void setTrackingStartedAtUtc(Instant trackingStartedAtUtc) {
        this.trackingStartedAtUtc = trackingStartedAtUtc;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}
