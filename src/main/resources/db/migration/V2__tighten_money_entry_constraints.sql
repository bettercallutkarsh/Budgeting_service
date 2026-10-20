-- V2: Tighten money_entries bucket rule and monthly_budgets income_source_label rule.
-- Forward-only. Does not modify V1. Fixes MySQL CHECK NULL loophole.
-- Test against budget_db_migration_test before applying to budget_db.

-- 1) money_entries.chk_money_entries_type_bucket
--    V1 allowed NULL bucket to slip through because MySQL CHECK yields UNKNOWN for NULL comparisons.
--    New rule requires explicit non-NULL bucket for EXPENSE / SAFETY_SAVING / INVESTMENT,
--    and forces bucket = NULL for CREDIT_CARD_PAYMENT.
ALTER TABLE money_entries
    DROP CHECK chk_money_entries_type_bucket,
    ADD CONSTRAINT chk_money_entries_type_bucket CHECK (
        (entry_type = 'EXPENSE'
            AND bucket IS NOT NULL
            AND bucket IN ('ESSENTIALS', 'SAFETY'))
        OR (entry_type = 'SAFETY_SAVING'
            AND bucket IS NOT NULL
            AND bucket = 'SAFETY')
        OR (entry_type = 'INVESTMENT'
            AND bucket IS NOT NULL
            AND bucket = 'INVESTMENTS')
        OR (entry_type = 'CREDIT_CARD_PAYMENT'
            AND bucket IS NULL)
    );

-- 2) monthly_budgets.chk_monthly_budgets_other_income_label
--    OTHER_INCOME: label may be NULL or meaningful nonblank text.
--    SALARY / POCKET_MONEY: label must be NULL (no free-text label allowed).
--    Empty or whitespace-only labels are rejected for every income type.
ALTER TABLE monthly_budgets
    DROP CHECK chk_monthly_budgets_other_income_label,
    ADD CONSTRAINT chk_monthly_budgets_other_income_label CHECK (
        (income_type = 'OTHER_INCOME'
            AND (income_source_label IS NULL
                 OR CHAR_LENGTH(TRIM(income_source_label)) > 0))
        OR (income_type IN ('SALARY', 'POCKET_MONEY')
            AND income_source_label IS NULL)
    );