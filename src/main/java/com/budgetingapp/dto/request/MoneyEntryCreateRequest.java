package com.budgetingapp.dto.request;

import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.entity.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public class MoneyEntryCreateRequest {

    @NotNull(message = "entryDate is required")
    private LocalDate entryDate;

    @NotNull(message = "entryTime is required")
    private LocalTime entryTime;

    @NotNull(message = "entryType is required")
    private EntryType entryType;

    private BudgetBucket bucket;

    @NotNull(message = "categoryCode is required")
    @Size(max = 80, message = "categoryCode must be at most 80 characters")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,79}", message = "categoryCode must be a stable uppercase code")
    private String categoryCode;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0.00")
    @Digits(integer = 12, fraction = 2, message = "amount must have at most 2 decimal places")
    private BigDecimal amount;

    private PaymentMethod paymentMethod;

    @Size(max = 500, message = "note must be at most 500 characters")
    private String note;

    @Size(max = 36, message = "clientRequestId must be at most 36 characters")
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
            message = "clientRequestId must be a UUID")
    private String clientRequestId;

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public LocalTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(LocalTime entryTime) {
        this.entryTime = entryTime;
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

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }
}
