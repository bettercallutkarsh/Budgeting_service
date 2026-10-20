-- Monthly Budgeting Money Planner — V1 core schema.
-- This migration creates three business tables only.
-- Review and test against a disposable empty MySQL 8.0.16+ schema before use.
-- Do not apply to a database with existing application tables without review.

CREATE TABLE users (
    user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone_number VARCHAR(16) NOT NULL,
    employment_type VARCHAR(24) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    opening_credit_card_outstanding_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    credit_card_tracking_started_at_utc DATETIME(6) NULL,
    account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_employment_type CHECK (
        employment_type IN ('SALARIED', 'STUDENT', 'UNEMPLOYED', 'SELF_EMPLOYED', 'OTHER')
    ),
    CONSTRAINT chk_users_card_opening_outstanding CHECK (
        opening_credit_card_outstanding_amount >= 0.00
    ),
    CONSTRAINT chk_users_account_status CHECK (
        account_status IN ('ACTIVE', 'DEACTIVATED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE monthly_budgets (
    monthly_budget_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    budget_month DATE NOT NULL,
    income_type VARCHAR(20) NOT NULL,
    income_source_label VARCHAR(120) NULL,
    income_amount DECIMAL(14,2) NOT NULL,
    allocation_mode VARCHAR(20) NOT NULL,
    investments_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    essentials_percentage DECIMAL(5,2) NOT NULL,
    safety_percentage DECIMAL(5,2) NOT NULL,
    investments_percentage DECIMAL(5,2) NOT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_monthly_budgets PRIMARY KEY (monthly_budget_id),
    CONSTRAINT uq_monthly_budgets_user_month UNIQUE (user_id, budget_month),
    CONSTRAINT fk_monthly_budgets_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT chk_monthly_budgets_month_first_day CHECK (DAYOFMONTH(budget_month) = 1),
    CONSTRAINT chk_monthly_budgets_income_type CHECK (
        income_type IN ('SALARY', 'POCKET_MONEY', 'OTHER_INCOME')
    ),
    CONSTRAINT chk_monthly_budgets_income_amount CHECK (income_amount >= 0.00),
    CONSTRAINT chk_monthly_budgets_allocation_mode CHECK (
        allocation_mode IN ('CONSERVATIVE', 'BALANCED', 'AGGRESSIVE', 'MANUAL')
    ),
    CONSTRAINT chk_monthly_budgets_percentage_ranges CHECK (
        essentials_percentage BETWEEN 0.00 AND 100.00
        AND safety_percentage BETWEEN 0.00 AND 100.00
        AND investments_percentage BETWEEN 0.00 AND 100.00
    ),
    CONSTRAINT chk_monthly_budgets_allocation_sum CHECK (
        (
            investments_enabled = TRUE
            AND essentials_percentage + safety_percentage + investments_percentage = 100.00
            AND (
                (allocation_mode = 'CONSERVATIVE'
                    AND essentials_percentage = 60.00
                    AND safety_percentage = 10.00
                    AND investments_percentage = 30.00)
                OR (allocation_mode = 'BALANCED'
                    AND essentials_percentage = 55.00
                    AND safety_percentage = 10.00
                    AND investments_percentage = 35.00)
                OR (allocation_mode = 'AGGRESSIVE'
                    AND essentials_percentage = 50.00
                    AND safety_percentage = 10.00
                    AND investments_percentage = 40.00)
                OR allocation_mode = 'MANUAL'
            )
        )
        OR (
            investments_enabled = FALSE
            AND allocation_mode = 'MANUAL'
            AND investments_percentage = 0.00
            AND essentials_percentage + safety_percentage = 100.00
        )
    ),
    CONSTRAINT chk_monthly_budgets_other_income_label CHECK (
        income_type = 'OTHER_INCOME'
        OR income_source_label IS NULL
        OR CHAR_LENGTH(TRIM(income_source_label)) > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE money_entries (
    money_entry_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    entry_at_utc DATETIME(6) NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    bucket VARCHAR(20) NULL,
    category_code VARCHAR(80) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    payment_method VARCHAR(24) NULL,
    note VARCHAR(500) NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'POSTED',
    client_request_id CHAR(36) NULL,
    voided_at_utc DATETIME(6) NULL,
    void_reason VARCHAR(250) NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_money_entries PRIMARY KEY (money_entry_id),
    CONSTRAINT fk_money_entries_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT uq_money_entries_user_request UNIQUE (user_id, client_request_id),
    CONSTRAINT chk_money_entries_type CHECK (
        entry_type IN ('EXPENSE', 'SAFETY_SAVING', 'INVESTMENT', 'CREDIT_CARD_PAYMENT')
    ),
    CONSTRAINT chk_money_entries_amount CHECK (amount > 0.00),
    CONSTRAINT chk_money_entries_status CHECK (status IN ('POSTED', 'VOID')),
    CONSTRAINT chk_money_entries_payment_method CHECK (
        payment_method IS NULL
        OR payment_method IN ('UPI', 'CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'WALLET', 'OTHER')
    ),
    CONSTRAINT chk_money_entries_type_bucket CHECK (
        (entry_type = 'EXPENSE' AND bucket IN ('ESSENTIALS', 'SAFETY'))
        OR (entry_type = 'SAFETY_SAVING' AND bucket = 'SAFETY')
        OR (entry_type = 'INVESTMENT' AND bucket = 'INVESTMENTS')
        OR (entry_type = 'CREDIT_CARD_PAYMENT' AND bucket IS NULL)
    ),
    CONSTRAINT chk_money_entries_card_method CHECK (
        (payment_method <> 'CREDIT_CARD' OR entry_type = 'EXPENSE')
        AND (entry_type <> 'CREDIT_CARD_PAYMENT' OR payment_method IS NULL
            OR payment_method <> 'CREDIT_CARD')
    ),
    CONSTRAINT chk_money_entries_payment_required CHECK (
        entry_type NOT IN ('EXPENSE', 'CREDIT_CARD_PAYMENT') OR payment_method IS NOT NULL
    ),
    CONSTRAINT chk_money_entries_void_metadata CHECK (
        (status = 'POSTED' AND voided_at_utc IS NULL)
        OR (status = 'VOID' AND voided_at_utc IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_money_entries_user_time
    ON money_entries (user_id, entry_at_utc);
CREATE INDEX idx_money_entries_user_type_time
    ON money_entries (user_id, entry_type, entry_at_utc);
CREATE INDEX idx_money_entries_user_bucket_time
    ON money_entries (user_id, bucket, entry_at_utc);
