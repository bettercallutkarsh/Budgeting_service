package com.budgetingapp.dto.response;

public class SipInstallmentPaymentResponse {

    private SipInstallmentResponse installment;

    private MoneyEntryResponse moneyEntry;

    private Boolean idempotentReplay;

    public SipInstallmentResponse getInstallment() {
        return installment;
    }

    public void setInstallment(SipInstallmentResponse installment) {
        this.installment = installment;
    }

    public MoneyEntryResponse getMoneyEntry() {
        return moneyEntry;
    }

    public void setMoneyEntry(MoneyEntryResponse moneyEntry) {
        this.moneyEntry = moneyEntry;
    }

    public Boolean getIdempotentReplay() {
        return idempotentReplay;
    }

    public void setIdempotentReplay(Boolean idempotentReplay) {
        this.idempotentReplay = idempotentReplay;
    }
}
