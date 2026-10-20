package com.budgetingapp.dto.response;

import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryStatus;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.entity.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;

public class MoneyEntryResponse {

    private Long moneyEntryId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant entryAtUtc;

    private EntryType entryType;

    private BudgetBucket bucket;

    private String categoryCode;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal amount;

    private PaymentMethod paymentMethod;

    private String note;

    private EntryStatus status;

    private String clientRequestId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant voidedAtUtc;

    private String voidReason;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAtUtc;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAtUtc;

    public Long getMoneyEntryId() {
        return moneyEntryId;
    }

    public void setMoneyEntryId(Long moneyEntryId) {
        this.moneyEntryId = moneyEntryId;
    }

    public Instant getEntryAtUtc() {
        return entryAtUtc;
    }

    public void setEntryAtUtc(Instant entryAtUtc) {
        this.entryAtUtc = entryAtUtc;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public BudgetBucket getBucket() {
        return bucket;
    }

    public void setBucket(BudgetBucket bucket) {
        this.bucket = bucket;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public EntryStatus getStatus() {
        return status;
    }

    public void setStatus(EntryStatus status) {
        this.status = status;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public Instant getVoidedAtUtc() {
        return voidedAtUtc;
    }

    public void setVoidedAtUtc(Instant voidedAtUtc) {
        this.voidedAtUtc = voidedAtUtc;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
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
