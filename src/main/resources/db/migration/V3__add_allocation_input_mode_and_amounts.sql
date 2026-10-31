-- V3: Add allocation input mode and persisted bucket amounts
-- Forward-only. Does not modify V1/V2. Backfills existing budgets as PERCENTAGE mode.
-- Test against budget_db_migration_test before applying to budget_db.

ALTER TABLE monthly_budgets
    ADD COLUMN allocation_input_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE' AFTER allocation_mode,
    ADD COLUMN essentials_amount DECIMAL(14,2) NULL AFTER investments_percentage,
    ADD COLUMN safety_amount DECIMAL(14,2) NULL AFTER essentials_amount,
    ADD COLUMN investments_amount DECIMAL(14,2) NULL AFTER safety_amount;

UPDATE monthly_budgets
SET essentials_amount = ROUND(income_amount * essentials_percentage / 100, 2),
    safety_amount = ROUND(income_amount * safety_percentage / 100, 2),
    investments_amount = income_amount - ROUND(income_amount * essentials_percentage / 100, 2) - ROUND(income_amount * safety_percentage / 100, 2);

ALTER TABLE monthly_budgets
    MODIFY COLUMN essentials_amount DECIMAL(14,2) NOT NULL,
    MODIFY COLUMN safety_amount DECIMAL(14,2) NOT NULL,
    MODIFY COLUMN investments_amount DECIMAL(14,2) NOT NULL,
    MODIFY COLUMN allocation_input_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE';

ALTER TABLE monthly_budgets
    ADD CONSTRAINT chk_monthly_budgets_allocation_input_mode CHECK (allocation_input_mode IN ('PERCENTAGE', 'AMOUNT'));

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
            AND allocation_mode = 'MANUAL'
            AND essentials_amount + safety_amount + investments_amount = income_amount
            AND essentials_amount >= 0.00
            AND safety_amount >= 0.00
            AND investments_amount >= 0.00
        )
    );
