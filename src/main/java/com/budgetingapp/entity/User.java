package com.budgetingapp.entity;

import com.budgetingapp.entity.enums.AccountStatus;
import com.budgetingapp.entity.enums.EmploymentType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uq_users_email", columnNames = "email"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long userId;

    @NotNull
    @Size(max = 120)
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @NotNull
    @Size(max = 254)
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @NotNull
    @Size(max = 16)
    @Column(name = "phone_number", nullable = false, length = 16)
    private String phoneNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 24)
    private EmploymentType employmentType;

    @NotNull
    @Size(max = 255)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotNull
    @Column(name = "opening_credit_card_outstanding_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingCreditCardOutstandingAmount = BigDecimal.ZERO;

    @Column(name = "credit_card_tracking_started_at_utc")
    private Instant creditCardTrackingStartedAtUtc;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 16)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "created_at_utc", nullable = false, insertable = false, updatable = false)
    private Instant createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false, insertable = false, updatable = false)
    private Instant updatedAtUtc;

    public User() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public BigDecimal getOpeningCreditCardOutstandingAmount() {
        return openingCreditCardOutstandingAmount;
    }

    public void setOpeningCreditCardOutstandingAmount(BigDecimal openingCreditCardOutstandingAmount) {
        this.openingCreditCardOutstandingAmount = openingCreditCardOutstandingAmount;
    }

    public Instant getCreditCardTrackingStartedAtUtc() {
        return creditCardTrackingStartedAtUtc;
    }

    public void setCreditCardTrackingStartedAtUtc(Instant creditCardTrackingStartedAtUtc) {
        this.creditCardTrackingStartedAtUtc = creditCardTrackingStartedAtUtc;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
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