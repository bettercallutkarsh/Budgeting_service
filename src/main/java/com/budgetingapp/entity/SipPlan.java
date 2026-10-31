package com.budgetingapp.entity;

import com.budgetingapp.entity.enums.SipPlanStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "sip_plans",
        indexes = {
                @Index(name = "idx_sip_plans_user_status", columnList = "user_id, status"),
                @Index(name = "idx_sip_plans_user_tracking", columnList = "user_id, tracking_start_date")
        })
public class SipPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sip_plan_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long sipPlanId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sip_plans_user"))
    private User user;

    @NotNull
    @Size(max = 120)
    @Column(name = "plan_name", nullable = false, length = 120)
    private String planName;

    @NotNull
    @Column(name = "monthly_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyAmount;

    @NotNull
    @Column(name = "installment_day", nullable = false)
    private Integer installmentDay;

    @NotNull
    @Column(name = "original_start_date", nullable = false)
    private LocalDate originalStartDate;

    @NotNull
    @Column(name = "tracking_start_date", nullable = false)
    private LocalDate trackingStartDate;

    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;

    @Column(name = "actual_ended_date")
    private LocalDate actualEndedDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SipPlanStatus status = SipPlanStatus.ACTIVE;

    @Column(name = "created_at_utc", nullable = false, insertable = false, updatable = false)
    private Instant createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false, insertable = false, updatable = false)
    private Instant updatedAtUtc;

    public SipPlan() {
    }

    public Long getSipPlanId() {
        return sipPlanId;
    }

    public void setSipPlanId(Long sipPlanId) {
        this.sipPlanId = sipPlanId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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
