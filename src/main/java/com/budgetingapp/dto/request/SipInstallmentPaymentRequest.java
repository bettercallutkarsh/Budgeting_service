package com.budgetingapp.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public class SipInstallmentPaymentRequest {

    private LocalDate paymentDate;

    private LocalTime paymentTime;

    @Size(max = 500, message = "note must be at most 500 characters")
    private String note;

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public LocalTime getPaymentTime() {
        return paymentTime;
    }

    public void setPaymentTime(LocalTime paymentTime) {
        this.paymentTime = paymentTime;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
