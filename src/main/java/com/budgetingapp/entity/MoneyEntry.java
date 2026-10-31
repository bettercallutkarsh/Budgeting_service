package com.budgetingapp.entity;

import com.budgetingapp.entity.enums.BudgetBucket;
import com.budgetingapp.entity.enums.EntryStatus;
import com.budgetingapp.entity.enums.EntryType;
import com.budgetingapp.entity.enums.PaymentMethod;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "money_entries",
        uniqueConstraints = @UniqueConstraint(name = "uq_money_entries_user_request", columnNames = {"user_id", "client_request_id"}),
        indexes = {
                @Index(name = "idx_money_entries_user_time", columnList = "user_id, entry_at_utc"),
                @Index(name = "idx_money_entries_user_type_time", columnList = "user_id, entry_type, entry_at_utc"),
                @Index(name = "idx_money_entries_user_bucket_time", columnList = "user_id, bucket, entry_at_utc")
        })
public class MoneyEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "money_entry_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long moneyEntryId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_money_entries_user"))
    private User user;

    @NotNull
    @Column(name = "entry_at_utc", nullable = false)
    private Instant entryAtUtc;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private EntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "bucket", length = 20)
    private BudgetBucket bucket;

    @NotNull
    @Size(max = 80)
    @Column(name = "category_code", nullable = false, length = 80)
    private String categoryCode;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 24)
    private PaymentMethod paymentMethod;

    @Size(max = 500)
    @Column(name = "note", length = 500)
    private String note;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EntryStatus status = EntryStatus.POSTED;

    @Column(name = "client_request_id", columnDefinition = "CHAR(36)")
    private String clientRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sip_plan_id", foreignKey = @ForeignKey(name = "fk_money_entries_sip_plan"))
    private SipPlan sipPlan;

    @Column(name = "sip_installment_period")
    private LocalDate sipInstallmentPeriod;

    @Column(name = "active_sip_installment_key", length = 64, insertable = false, updatable = false)
    private String activeSipInstallmentKey;

    @Column(name = "voided_at_utc")
    private Instant voidedAtUtc;

    @Size(max = 250)
    @Column(name = "void_reason", length = 250)
    private String voidReason;

    @Column(name = "created_at_utc", nullable = false, insertable = false, updatable = false)
    private Instant createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false, insertable = false, updatable = false)
    private Instant updatedAtUtc;

    public MoneyEntry() {
    }

    public Long getMoneyEntryId() {
        return moneyEntryId;
    }

    public void setMoneyEntryId(Long moneyEntryId) {
        this.moneyEntryId = moneyEntryId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public SipPlan getSipPlan() {
        return sipPlan;
    }

    public void setSipPlan(SipPlan sipPlan) {
        this.sipPlan = sipPlan;
    }

    public LocalDate getSipInstallmentPeriod() {
        return sipInstallmentPeriod;
    }

    public void setSipInstallmentPeriod(LocalDate sipInstallmentPeriod) {
        this.sipInstallmentPeriod = sipInstallmentPeriod;
    }

    public String getActiveSipInstallmentKey() {
        return activeSipInstallmentKey;
    }

    public void setActiveSipInstallmentKey(String activeSipInstallmentKey) {
        this.activeSipInstallmentKey = activeSipInstallmentKey;
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