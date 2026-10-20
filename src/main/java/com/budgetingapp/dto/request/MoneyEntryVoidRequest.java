package com.budgetingapp.dto.request;

import jakarta.validation.constraints.Size;

public class MoneyEntryVoidRequest {

    @Size(max = 250, message = "voidReason must be at most 250 characters")
    private String voidReason;

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }
}
