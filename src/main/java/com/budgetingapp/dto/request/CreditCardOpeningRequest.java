package com.budgetingapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public class CreditCardOpeningRequest {

    @NotNull(message = "openingOutstandingAmount is required")
    @DecimalMin(value = "0.00", message = "openingOutstandingAmount must not be negative")
    @Digits(integer = 12, fraction = 2, message = "openingOutstandingAmount must have at most 2 decimal places")
    private BigDecimal openingOutstandingAmount;

    private Instant trackingStartedAtUtc;

    public BigDecimal getOpeningOutstandingAmount() {
        return openingOutstandingAmount;
    }

    public void setOpeningOutstandingAmount(BigDecimal openingOutstandingAmount) {
        this.openingOutstandingAmount = openingOutstandingAmount;
    }

    public Instant getTrackingStartedAtUtc() {
        return trackingStartedAtUtc;
    }

    public void setTrackingStartedAtUtc(Instant trackingStartedAtUtc) {
        this.trackingStartedAtUtc = trackingStartedAtUtc;
    }
}
