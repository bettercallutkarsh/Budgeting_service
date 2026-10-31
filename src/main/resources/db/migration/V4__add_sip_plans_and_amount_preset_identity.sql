-- V4: Preserve allocation preset identity in amount mode and add SIP plans.
-- Forward-only. Does not modify V1/V2/V3. Test against budget_db_migration_test first.

ALTER TABLE monthly_budgets
    DROP CHECK chk_monthly_budgets_allocation_sum;

ALTER TABLE monthly_budgets
    ADD CONSTRAINT chk_monthly_budgets_allocation_sum CHECK (
        (
            investments_enabled = TRUE
            AND allocation_input_mode = 'PERCENTAGE'
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
            AND essentials_amount + safety_amount + investments_amount = income_amount
        )
        OR (
            investments_enabled = FALSE
            AND allocation_mode = 'MANUAL'
            AND allocation_input_mode IN ('PERCENTAGE', 'AMOUNT')
            AND investments_percentage = 0.00
            AND investments_amount = 0.00
            AND essentials_percentage + safety_percentage = 100.00
            AND essentials_amount + safety_amount = income_amount
        )
        OR (
            investments_enabled = TRUE
            AND allocation_input_mode = 'AMOUNT'
            AND essentials_amount + safety_amount + investments_amount = income_amount
            AND essentials_amount >= 0.00
            AND safety_amount >= 0.00
            AND investments_amount >= 0.00
        )
    );

CREATE TABLE sip_plans (
    sip_plan_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    plan_name VARCHAR(120) NOT NULL,
    monthly_amount DECIMAL(14,2) NOT NULL,
    installment_day INT NOT NULL,
    original_start_date DATE NOT NULL,
    tracking_start_date DATE NOT NULL,
    planned_end_date DATE NULL,
    actual_ended_date DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_sip_plans PRIMARY KEY (sip_plan_id),
    CONSTRAINT fk_sip_plans_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT chk_sip_plans_name CHECK (CHAR_LENGTH(TRIM(plan_name)) > 0),
    CONSTRAINT chk_sip_plans_monthly_amount CHECK (monthly_amount > 0.00),
    CONSTRAINT chk_sip_plans_installment_day CHECK (installment_day BETWEEN 1 AND 31),
    CONSTRAINT chk_sip_plans_dates CHECK (
        tracking_start_date >= original_start_date
        AND (planned_end_date IS NULL OR planned_end_date >= tracking_start_date)
        AND (actual_ended_date IS NULL OR actual_ended_date >= tracking_start_date)
    ),
    CONSTRAINT chk_sip_plans_status CHECK (
        status IN ('ACTIVE', 'STOPPED', 'COMPLETED')
        AND (
            (status = 'ACTIVE' AND actual_ended_date IS NULL)
            OR (status IN ('STOPPED', 'COMPLETED') AND actual_ended_date IS NOT NULL)
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_sip_plans_user_status
    ON sip_plans (user_id, status);
CREATE INDEX idx_sip_plans_user_tracking
    ON sip_plans (user_id, tracking_start_date);

ALTER TABLE money_entries
    ADD COLUMN sip_plan_id BIGINT UNSIGNED NULL AFTER client_request_id,
    ADD COLUMN sip_installment_period DATE NULL AFTER sip_plan_id,
    ADD COLUMN active_sip_installment_key VARCHAR(64)
        GENERATED ALWAYS AS (
            CASE
                WHEN sip_plan_id IS NOT NULL
                    AND sip_installment_period IS NOT NULL
                    AND status = 'POSTED'
                THEN CONCAT(sip_plan_id, ':', sip_installment_period)
                ELSE NULL
            END
        ) STORED AFTER sip_installment_period,
    ADD CONSTRAINT fk_money_entries_sip_plan FOREIGN KEY (sip_plan_id)
        REFERENCES sip_plans (sip_plan_id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_money_entries_sip_link CHECK (
        (sip_plan_id IS NULL AND sip_installment_period IS NULL)
        OR (
            sip_plan_id IS NOT NULL
            AND sip_installment_period IS NOT NULL
            AND entry_type = 'INVESTMENT'
            AND bucket = 'INVESTMENTS'
            AND category_code = 'SIP'
            AND DAYOFMONTH(sip_installment_period) = 1
        )
    );

CREATE UNIQUE INDEX uq_money_entries_active_sip_installment
    ON money_entries (active_sip_installment_key);
CREATE INDEX idx_money_entries_sip_period
    ON money_entries (sip_plan_id, sip_installment_period);
