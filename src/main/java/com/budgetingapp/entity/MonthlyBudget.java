package com.budgetingapp.entity;

import com.budgetingapp.entity.enums.AllocationInputMode;
import com.budgetingapp.entity.enums.AllocationMode;
import com.budgetingapp.entity.enums.IncomeType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "monthly_budgets",
        uniqueConstraints = @UniqueConstraint(name = "uq_monthly_budgets_user_month", columnNames = {"user_id", "budget_month"}))
public class MonthlyBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "monthly_budget_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long monthlyBudgetId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_monthly_budgets_user"))
    private User user;

    @NotNull
    @Column(name = "budget_month", nullable = false)
    private LocalDate budgetMonth;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "income_type", nullable = false, length = 20)
    private IncomeType incomeType;

    @Column(name = "income_source_label", length = 120)
    private String incomeSourceLabel;

    @NotNull
    @Column(name = "income_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal incomeAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_mode", nullable = false, length = 20)
    private AllocationMode allocationMode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_input_mode", nullable = false, length = 20)
    private AllocationInputMode allocationInputMode = AllocationInputMode.PERCENTAGE;

    @NotNull
    @Column(name = "investments_enabled", nullable = false)
    private Boolean investmentsEnabled = Boolean.TRUE;

    @NotNull
    @Column(name = "essentials_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal essentialsPercentage;

    @NotNull
    @Column(name = "safety_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal safetyPercentage;

    @NotNull
    @Column(name = "investments_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal investmentsPercentage;

    @NotNull
    @Column(name = "essentials_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal essentialsAmount;

    @NotNull
    @Column(name = "safety_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal safetyAmount;

    @NotNull
    @Column(name = "investments_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal investmentsAmount;

    @Column(name = "created_at_utc", nullable = false, insertable = false, updatable = false)
    private Instant createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false, insertable = false, updatable = false)
    private Instant updatedAtUtc;

    public MonthlyBudget() {
    }

    public Long getMonthlyBudgetId() {
        return monthlyBudgetId;
    }

    public void setMonthlyBudgetId(Long monthlyBudgetId) {
        this.monthlyBudgetId = monthlyBudgetId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDate getBudgetMonth() {
        return budgetMonth;
    }

    public void setBudgetMonth(LocalDate budgetMonth) {
        this.budgetMonth = budgetMonth;
    }

    public IncomeType getIncomeType() {
        return incomeType;
    }

    public void setIncomeType(IncomeType incomeType) {
        this.incomeType = incomeType;
    }

    public String getIncomeSourceLabel() {
        return incomeSourceLabel;
    }

    public void setIncomeSourceLabel(String incomeSourceLabel) {
        this.incomeSourceLabel = incomeSourceLabel;
    }

    public BigDecimal getIncomeAmount() {
        return incomeAmount;
    }

    public void setIncomeAmount(BigDecimal incomeAmount) {
        this.incomeAmount = incomeAmount;
    }

    public AllocationMode getAllocationMode() {
        return allocationMode;
    }

    public void setAllocationMode(AllocationMode allocationMode) {
        this.allocationMode = allocationMode;
    }

    public AllocationInputMode getAllocationInputMode() {
        return allocationInputMode;
    }

    public void setAllocationInputMode(AllocationInputMode allocationInputMode) {
        this.allocationInputMode = allocationInputMode;
    }

    public Boolean getInvestmentsEnabled() {
        return investmentsEnabled;
    }

    public void setInvestmentsEnabled(Boolean investmentsEnabled) {
        this.investmentsEnabled = investmentsEnabled;
    }

    public BigDecimal getEssentialsPercentage() {
        return essentialsPercentage;
    }

    public void setEssentialsPercentage(BigDecimal essentialsPercentage) {
        this.essentialsPercentage = essentialsPercentage;
    }

    public BigDecimal getSafetyPercentage() {
        return safetyPercentage;
    }

    public void setSafetyPercentage(BigDecimal safetyPercentage) {
        this.safetyPercentage = safetyPercentage;
    }

    public BigDecimal getInvestmentsPercentage() {
        return investmentsPercentage;
    }

    public void setInvestmentsPercentage(BigDecimal investmentsPercentage) {
        this.investmentsPercentage = investmentsPercentage;
    }

    public BigDecimal getEssentialsAmount() {
        return essentialsAmount;
    }

    public void setEssentialsAmount(BigDecimal essentialsAmount) {
        this.essentialsAmount = essentialsAmount;
    }

    public BigDecimal getSafetyAmount() {
        return safetyAmount;
    }

    public void setSafetyAmount(BigDecimal safetyAmount) {
        this.safetyAmount = safetyAmount;
    }

    public BigDecimal getInvestmentsAmount() {
        return investmentsAmount;
    }

    public void setInvestmentsAmount(BigDecimal investmentsAmount) {
        this.investmentsAmount = investmentsAmount;
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
