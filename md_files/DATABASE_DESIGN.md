# Database Design — Detailed Data Dictionary

**Version:** Draft 0.1  
**Status:** Review required; not executable DDL  
**Database:** `budget_db`

## 1. Product and scope assumptions

- The product supports Yearly → Monthly → Daily planning for Indian individuals, couples and families.
- V1 scope includes authentication, household foundation, accounts, manual income/expense tracking, monthly budget, yearly/monthly planning foundation, safe-to-spend after formula approval, recurring commitments, bills, credit cards, goals, monthly review and basic privacy/security.
- V2/V3 features such as investments, net worth, EPF/PPF/NPS, tax, import, bank integrations and AI insights must fit the domain model but do not all need V1 tables.
- Database engine target is MySQL 8.x with InnoDB and `utf8mb4`.
- Backend owns validation and authoritative financial calculations; use Java `BigDecimal`, not floating-point types.

## 2. Conventions

- Table and column names: lowercase `snake_case`. Catalog tables preserve the concept `t_catalog` and `t_catalog_code`.
- Primary keys: `BIGINT UNSIGNED AUTO_INCREMENT`, except `user_preferences.user_id` and `credit_card_details.account_id`, which are one-to-one primary key/foreign key candidates.
- Timestamps: `DATETIME(6)`; application writes UTC. MySQL `DATETIME` does not itself carry timezone metadata.
- Money: proposed `DECIMAL(19,4)` for persisted monetary amounts. INR input/posting is rounded according to the approved currency policy (normally 2 minor units); do not treat extra storage scale as permission to bypass currency rules.
- Percentages: `DECIMAL(7,4)` for allocation percentages; alert threshold may use `DECIMAL(5,2)`.
- Dates: use `DATE` for transaction dates, plan months, due dates and period boundaries; `DATETIME(6)` for event timestamps.
- Flags: `BOOLEAN NOT NULL DEFAULT ...` where appropriate.
- Avoid storing balances/totals that can be deterministically calculated, unless a reviewed snapshot/performance requirement justifies a snapshot table.
- Financial records should not be hard-deleted casually. Use `VOID`, reversal or archive semantics where appropriate.
- Every household-owned row must be authorized against the authenticated user's current membership and permissions.

### FK/tenant rule

For household-owned tables, prefer both `household_id` and the referenced record's ID in a composite FK when practical (backed by a matching `UNIQUE(household_id, <id>)`). This prevents a transaction in household A from referencing an account/category in household B at the database layer. Application authorization remains mandatory.

### Catalog lookup rule

For a `*_code_id` FK into `t_catalog_code`, the service must also validate that the selected value belongs to the expected `catalog_code`, is active, and is allowed for this operation. A generic FK alone only confirms that the value row exists; it does not guarantee it belongs to the intended catalog.

---

# 3. Table inventory and column definitions

Legend: **PK** primary key, **FK** foreign key, **UQ** unique key, **NN** not null. Unless marked nullable, a field is required. `created_at` / `updated_at` use `DATETIME(6)`.

## A. Reference data and identity

### 1. `t_catalog` — configurable catalog definitions

| Column | Type | Constraints / intent |
|---|---|---|
| `catalog_id` | `BIGINT UNSIGNED` | PK |
| `catalog_code` | `VARCHAR(80)` | NN, UQ; immutable stable code, e.g. `PAYMENT_METHOD` |
| `catalog_name` | `VARCHAR(120)` | NN; display name |
| `description` | `VARCHAR(500)` | Nullable |
| `is_active` | `BOOLEAN` | NN, default true |
| `created_at` | `DATETIME(6)` | NN |
| `updated_at` | `DATETIME(6)` | NN |

### 2. `t_catalog_code` — values within a catalog

| Column | Type | Constraints / intent |
|---|---|---|
| `catalog_code_id` | `BIGINT UNSIGNED` | PK |
| `catalog_id` | `BIGINT UNSIGNED` | NN, FK → `t_catalog` |
| `value_code` | `VARCHAR(80)` | NN; stable machine code |
| `value_label` | `VARCHAR(120)` | NN; display label |
| `description` | `VARCHAR(500)` | Nullable |
| `display_order` | `INT` | NN, default 0 |
| `is_default` | `BOOLEAN` | NN, default false |
| `is_active` | `BOOLEAN` | NN, default true; deactivate instead of deleting used values |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |
| `UNIQUE` | — | `(catalog_id, value_code)`; also expose `UNIQUE(catalog_id, catalog_code_id)` if using composite validation FKs |

### 3. `users` — human identity/profile

| Column | Type | Constraints / intent |
|---|---|---|
| `user_id` | `BIGINT UNSIGNED` | PK |
| `email` | `VARCHAR(254)` | NN; display/canonical email |
| `email_normalized` | `VARCHAR(254)` | NN, UQ; normalized consistently in application |
| `display_name` | `VARCHAR(120)` | NN |
| `phone_number` | `VARCHAR(20)` | Nullable; optional |
| `account_status` | `VARCHAR(30)` | NN; code-defined: `ACTIVE`, `SUSPENDED`, `PENDING_VERIFICATION`, `CLOSED` |
| `email_verified_at` | `DATETIME(6)` | Nullable |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

### 4. `user_credentials` — authentication secret material

| Column | Type | Constraints / intent |
|---|---|---|
| `credential_id` | `BIGINT UNSIGNED` | PK |
| `user_id` | `BIGINT UNSIGNED` | NN, FK → `users`, UQ (one credential row per user for password auth) |
| `password_hash` | `VARCHAR(255)` | NN; Argon2id/bcrypt encoded hash, never plaintext |
| `password_updated_at` | `DATETIME(6)` | Nullable |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

OAuth/social identity provider mappings and refresh/session tokens are separate future design items; do not store raw access tokens here.

### 5. `user_preferences` — user-specific presentation settings

| Column | Type | Constraints / intent |
|---|---|---|
| `user_id` | `BIGINT UNSIGNED` | PK + FK → `users` |
| `locale` | `VARCHAR(20)` | NN, default `en-IN` |
| `timezone` | `VARCHAR(60)` | NN, default `Asia/Kolkata` |
| `preferred_currency_code` | `CHAR(3)` | NN, default `INR` |
| `date_format` | `VARCHAR(30)` | NN, default product-defined format |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

### 6. `households` — security and shared-data boundary

| Column | Type | Constraints / intent |
|---|---|---|
| `household_id` | `BIGINT UNSIGNED` | PK |
| `household_name` | `VARCHAR(120)` | NN |
| `created_by_user_id` | `BIGINT UNSIGNED` | NN, FK → `users` |
| `default_currency_code` | `CHAR(3)` | NN, default `INR`; V1 recommended single household currency |
| `status` | `VARCHAR(20)` | NN; `ACTIVE`, `ARCHIVED`, `CLOSED` |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

### 7. `household_members` — user-to-household membership

| Column | Type | Constraints / intent |
|---|---|---|
| `household_member_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN, FK → `households` |
| `user_id` | `BIGINT UNSIGNED` | NN, FK → `users` |
| `role_code` | `VARCHAR(30)` | NN; `OWNER`, `ADMIN`, `MEMBER`, `VIEWER` (final role matrix to approve) |
| `status` | `VARCHAR(20)` | NN; `INVITED`, `ACTIVE`, `REMOVED` |
| `joined_at` | `DATETIME(6)` | Nullable |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |
| `UNIQUE` | — | `(household_id, user_id)`; reinviting changes membership state rather than creating duplicate row |

### 8. `household_invitations` — optional V1 sharing workflow

| Column | Type | Constraints / intent |
|---|---|---|
| `invitation_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN, FK → `households` |
| `invited_email_normalized` | `VARCHAR(254)` | NN |
| `role_code` | `VARCHAR(30)` | NN |
| `invited_by_member_id` | `BIGINT UNSIGNED` | NN, FK → `household_members` |
| `token_hash` | `VARCHAR(255)` | NN; hash only, never raw invitation token |
| `status` | `VARCHAR(20)` | NN; `PENDING`, `ACCEPTED`, `EXPIRED`, `REVOKED` |
| `expires_at` | `DATETIME(6)` | NN |
| `accepted_at` | `DATETIME(6)` | Nullable |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

If invitations are not part of the first release, defer the table until the sharing API phase.

---

## B. Accounts, categories and actual financial activity

### 9. `accounts` — cash/bank/wallet/credit card account containers

| Column | Type | Constraints / intent |
|---|---|---|
| `account_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN, FK → `households`; add UQ `(household_id, account_id)` for composite tenant FKs |
| `account_name` | `VARCHAR(120)` | NN |
| `account_type_code_id` | `BIGINT UNSIGNED` | NN, FK → catalog value in `ACCOUNT_TYPE` |
| `institution_name` | `VARCHAR(120)` | Nullable |
| `last_four_digits` | `CHAR(4)` | Nullable; never store full card/account number unless justified and protected |
| `currency_code` | `CHAR(3)` | NN, default inherited as `INR`; do not silently change after transactions exist |
| `opening_balance_amount` | `DECIMAL(19,4)` | NN, default 0; meaning depends on account type |
| `opening_balance_date` | `DATE` | NN |
| `owner_member_id` | `BIGINT UNSIGNED` | Nullable FK → `household_members`; personal account owner |
| `visibility_scope` | `VARCHAR(20)` | NN; `HOUSEHOLD` or `OWNER_ONLY` |
| `include_in_net_worth` | `BOOLEAN` | NN, default true for relevant accounts |
| `status` | `VARCHAR(20)` | NN; `ACTIVE`, `ARCHIVED`, `CLOSED` |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

Derived balances are not stored as the main truth. Do not change currency or opening balance after posting transactions without an audited correction workflow.

### 10. `credit_card_details` — subtype extension for credit-card accounts

| Column | Type | Constraints / intent |
|---|---|---|
| `account_id` | `BIGINT UNSIGNED` | PK + FK → `accounts` |
| `credit_limit` | `DECIMAL(19,4)` | NN, non-negative |
| `statement_day` | `TINYINT UNSIGNED` | Nullable; normally 1–31, issuer-specific handling needed |
| `payment_due_day` | `TINYINT UNSIGNED` | Nullable; fallback/default only, actual statements store exact dates |
| `issuer_name` | `VARCHAR(120)` | Nullable |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

Service must validate that the parent account is of type `CREDIT_CARD`. Outstanding, amount used and available limit are calculated, not independently editable fields.

### 11. `categories` — household-managed categories/subcategories

| Column | Type | Constraints / intent |
|---|---|---|
| `category_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN, FK → `households`; UQ `(household_id, category_id)` |
| `category_name` | `VARCHAR(100)` | NN |
| `category_name_normalized` | `VARCHAR(100)` | NN; supports case/space normalization and duplicate checks |
| `category_type` | `VARCHAR(20)` | NN; `INCOME` or `EXPENSE` |
| `parent_category_id` | `BIGINT UNSIGNED` | Nullable; self-FK constrained to same household |
| `default_spending_behavior_code_id` | `BIGINT UNSIGNED` | Nullable; catalog `SPENDING_BEHAVIOR`; default only, transaction split may override |
| `is_system_default` | `BOOLEAN` | NN, default false; indicates category was seeded from product defaults |
| `is_active` | `BOOLEAN` | NN, default true |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

Seed initial Indian categories when a household is created. User-defined category data does not belong in `t_catalog_code`.

### 12. `payees` — merchants, employers and sources

| Column | Type | Constraints / intent |
|---|---|---|
| `payee_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN, FK → `households`; UQ `(household_id, payee_id)` |
| `payee_name` | `VARCHAR(150)` | NN |
| `payee_name_normalized` | `VARCHAR(150)` | NN |
| `payee_type_code` | `VARCHAR(30)` | Nullable; code-defined broad type such as `MERCHANT`, `EMPLOYER`, `PERSON`, `OTHER` |
| `is_active` | `BOOLEAN` | NN, default true |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

### 13. `transactions` — actual posted or draft income/expense/refund activity

| Column | Type | Constraints / intent |
|---|---|---|
| `transaction_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; UQ `(household_id, transaction_id)` |
| `account_id` | `BIGINT UNSIGNED` | NN; FK to account in same household |
| `transaction_type` | `VARCHAR(20)` | NN; `INCOME`, `EXPENSE`, `REFUND`, `ADJUSTMENT`; transfer is a separate table |
| `transaction_date` | `DATE` | NN; business date |
| `amount` | `DECIMAL(19,4)` | NN, greater than 0; positive absolute amount, type determines meaning |
| `currency_code` | `CHAR(3)` | NN; must match account currency for V1 same-currency activity |
| `payee_id` | `BIGINT UNSIGNED` | Nullable; same-household FK |
| `payment_method_code_id` | `BIGINT UNSIGNED` | Nullable FK to `PAYMENT_METHOD` catalog value |
| `spender_member_id` | `BIGINT UNSIGNED` | Nullable; same-household member, person who spent/received it |
| `income_source_id` | `BIGINT UNSIGNED` | Nullable; FK → `income_sources`, when applicable |
| `related_transaction_id` | `BIGINT UNSIGNED` | Nullable self-FK, e.g. refund/reversal relates to original transaction |
| `description` | `VARCHAR(255)` | Nullable |
| `notes` | `TEXT` | Nullable |
| `status` | `VARCHAR(20)` | NN; `DRAFT`, `POSTED`, `VOID` |
| `source_kind` | `VARCHAR(20)` | NN; `MANUAL`, `RECURRING`, `IMPORT`, `SYSTEM` |
| `created_by_user_id` | `BIGINT UNSIGNED` | NN, FK → `users` |
| `updated_by_user_id` | `BIGINT UNSIGNED` | Nullable FK → `users` |
| `posted_at` | `DATETIME(6)` | Nullable until posted |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

Do **not** put `category_id` on the header; `transaction_splits` is the single source for category attribution. For a normal single-category transaction, create one split. Cross-household FKs must be prevented by composite constraints where practical and by service checks always.

### 14. `transaction_splits` — category attribution for a transaction

| Column | Type | Constraints / intent |
|---|---|---|
| `transaction_split_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; tenant-safe FK pattern |
| `transaction_id` | `BIGINT UNSIGNED` | NN; parent transaction in same household |
| `category_id` | `BIGINT UNSIGNED` | NN; category in same household |
| `amount` | `DECIMAL(19,4)` | NN, greater than 0 |
| `spending_behavior_code_id` | `BIGINT UNSIGNED` | Nullable; catalog `SPENDING_BEHAVIOR`, e.g. fixed/variable/flexible |
| `notes` | `VARCHAR(255)` | Nullable |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

For a posted transaction, split amounts must sum to the header amount. This cross-row aggregate invariant is validated by the transaction service within one database transaction; a simple row CHECK cannot enforce it.

### 15. `account_transfers` — transfer/settlement between accounts

| Column | Type | Constraints / intent |
|---|---|---|
| `transfer_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; UQ `(household_id, transfer_id)` |
| `from_account_id` | `BIGINT UNSIGNED` | NN; same-household FK |
| `to_account_id` | `BIGINT UNSIGNED` | NN; same-household FK |
| `transfer_date` | `DATE` | NN |
| `payment_method_code_id` | `BIGINT UNSIGNED` | Nullable FK to `PAYMENT_METHOD` catalog value |
| `amount` | `DECIMAL(19,4)` | NN, greater than 0; principal only |
| `fee_amount` | `DECIMAL(19,4)` | NN, default 0; document treatment before implementation |
| `fee_transaction_id` | `BIGINT UNSIGNED` | Nullable FK to a posted expense transaction for a separately recorded fee |
| `description` | `VARCHAR(255)` | Nullable |
| `status` | `VARCHAR(20)` | NN; `PENDING`, `POSTED`, `VOID` |
| `created_by_user_id` | `BIGINT UNSIGNED` | NN, FK → `users` |
| `posted_at` | `DATETIME(6)` | Nullable |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

Source and destination must differ. V1 should reject cross-currency transfers until explicit FX fields/rules are designed. Transfer principal is not an expense; card settlement reduces liability rather than recording the purchase again.

---

## C. Income and planning

### 16. `income_sources` — named source, not the actual receipt

| Column | Type | Constraints / intent |
|---|---|---|
| `income_source_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; UQ `(household_id, income_source_id)` |
| `source_name` | `VARCHAR(120)` | NN, e.g. salary, rental income, freelance |
| `source_type_code` | `VARCHAR(40)` | NN; code-defined/customizable classification agreed in catalog spec |
| `default_payee_id` | `BIGINT UNSIGNED` | Nullable; same-household FK to payees |
| `default_account_id` | `BIGINT UNSIGNED` | Nullable; same-household FK to accounts |
| `is_active` | `BOOLEAN` | NN, default true |
| `notes` | `VARCHAR(500)` | Nullable |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

Do not require one fixed amount/frequency here; salary can change and bonuses can be one-off. Planned occurrences hold their own amounts and dates.

### 17. `planned_income_entries` — specific expected receipt in a plan period

| Column | Type | Constraints / intent |
|---|---|---|
| `planned_income_entry_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN |
| `financial_year_plan_id` | `BIGINT UNSIGNED` | Nullable; link to annual plan when in annual forecast |
| `monthly_plan_id` | `BIGINT UNSIGNED` | Nullable; link to month plan when directly planned for a month |
| `income_source_id` | `BIGINT UNSIGNED` | NN; same-household FK |
| `expected_date` | `DATE` | NN |
| `income_classification_code_id` | `BIGINT UNSIGNED` | NN; catalog `INCOME_CLASSIFICATION`: `GUARANTEED`, `EXPECTED`, `VARIABLE`, `ONE_TIME` |
| `expected_amount` | `DECIMAL(19,4)` | NN, greater than 0 |
| `currency_code` | `CHAR(3)` | NN; household/plan currency in V1 |
| `confidence_note` | `VARCHAR(255)` | Nullable |
| `actual_transaction_id` | `BIGINT UNSIGNED` | Nullable, UQ; linked actual receipt, if matched |
| `status` | `VARCHAR(20)` | NN; `PLANNED`, `RECEIVED`, `CANCELLED`, `MISSED` |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

Exactly one parent period may be set, unless explicit product logic permits unlinked planning rows: enforce `(financial_year_plan_id IS NOT NULL) XOR (monthly_plan_id IS NOT NULL)` in a CHECK/service rule. Do not count expected and actual amounts as two income events.

### 18. `financial_year_plans` — yearly plan/scenario/version

| Column | Type | Constraints / intent |
|---|---|---|
| `financial_year_plan_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; UQ tenant key |
| `plan_name` | `VARCHAR(120)` | NN |
| `period_basis` | `VARCHAR(30)` | NN; `INDIAN_FINANCIAL_YEAR`, `CALENDAR_YEAR`, `CUSTOM` |
| `period_start` | `DATE` | NN |
| `period_end` | `DATE` | NN, inclusive; greater than or equal to start |
| `version_number` | `INT UNSIGNED` | NN, starts at 1 |
| `is_primary_plan` | `BOOLEAN` | NN, default false; at most one primary for a household/period, enforced by service/strategy |
| `status` | `VARCHAR(20)` | NN; `DRAFT`, `ACTIVE`, `CLOSED`, `ARCHIVED` |
| `created_by_user_id` | `BIGINT UNSIGNED` | NN, FK → `users` |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |
| `UNIQUE` | — | `(household_id, period_start, period_end, version_number)` |

Default Indian FY is 1 April–31 March, but actual dates are stored. Calendar-year mode is supported by a different period basis. Annual totals are aggregated from detailed plan/actual records rather than duplicated as authoritative numbers.

### 19. `monthly_plans` — one month inside a yearly plan version

| Column | Type | Constraints / intent |
|---|---|---|
| `monthly_plan_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; tenant-safe FK |
| `financial_year_plan_id` | `BIGINT UNSIGNED` | NN; parent yearly plan in same household |
| `month_start_date` | `DATE` | NN; must be first day of month and within parent period |
| `status` | `VARCHAR(20)` | NN; `DRAFT`, `ACTIVE`, `REVIEWED`, `CLOSED` |
| `selected_allocation_template_id` | `BIGINT UNSIGNED` | Nullable FK → `allocation_templates` |
| `reviewed_at` | `DATETIME(6)` | Nullable |
| `created_by_user_id` | `BIGINT UNSIGNED` | NN, FK → `users` |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |
| `UNIQUE` | — | `(financial_year_plan_id, month_start_date)` |

Do not store `planned_income_amount` as an independent authoritative total if planned income entries already define it; aggregate entries to avoid drift.

### 20. `allocation_templates` — versioned allocation presets/custom templates

| Column | Type | Constraints / intent |
|---|---|---|
| `allocation_template_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | Nullable; NULL means system template, non-NULL means household custom template |
| `template_code` | `VARCHAR(60)` | NN; stable code for system entries |
| `template_name` | `VARCHAR(120)` | NN |
| `allocation_mode` | `VARCHAR(30)` | NN; `CONSERVATIVE`, `BALANCED`, `AGGRESSIVE`, `CUSTOM` |
| `version_number` | `INT UNSIGNED` | NN |
| `is_system_template` | `BOOLEAN` | NN, default false |
| `is_active` | `BOOLEAN` | NN, default true |
| `created_by_user_id` | `BIGINT UNSIGNED` | Nullable FK → `users` |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

A template version used in a finalized month should be immutable; create a new version for changes.

### 21. `allocation_template_items` — percentages inside a template

| Column | Type | Constraints / intent |
|---|---|---|
| `allocation_template_item_id` | `BIGINT UNSIGNED` | PK |
| `allocation_template_id` | `BIGINT UNSIGNED` | NN, FK → `allocation_templates` |
| `allocation_group_code` | `VARCHAR(40)` | NN; `PRIMARY`, `ASSET_CLASS`, `EQUITY_SUBCLASS`, `DEBT_SUBCLASS`, `COMMODITY`, `GOAL` |
| `allocation_code` | `VARCHAR(60)` | NN; e.g. `NEEDS`, `EMERGENCY`, `INVESTMENTS`, `EQUITY`, `RETIREMENT` |
| `parent_allocation_code` | `VARCHAR(60)` | Nullable; allows nested subsets (e.g. `LARGE_CAP` under `EQUITY`) |
| `percentage` | `DECIMAL(7,4)` | NN; 0–100 inclusive |
| `is_enabled` | `BOOLEAN` | NN, default true; crypto should be disabled by default per current requirement |
| `display_order` | `INT` | NN, default 0 |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |
| `UNIQUE` | — | Template + allocation group + parent + allocation code (nullable-parent uniqueness needs service validation or normalized parent key) |

For each enabled group, percentage totals are validated against that group's base. For nested groups, child percentages apply to the parent allocation, not automatically to total household income.

### 22. `monthly_plan_allocations` — frozen/overridden amounts for a monthly plan

| Column | Type | Constraints / intent |
|---|---|---|
| `monthly_plan_allocation_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; tenant-safe FK |
| `monthly_plan_id` | `BIGINT UNSIGNED` | NN, FK to monthly plan in same household |
| `allocation_group_code` | `VARCHAR(40)` | NN, same group vocabulary as template |
| `allocation_code` | `VARCHAR(60)` | NN |
| `parent_allocation_code` | `VARCHAR(60)` | Nullable |
| `planned_percentage` | `DECIMAL(7,4)` | NN, 0–100 |
| `planned_amount` | `DECIMAL(19,4)` | NN, non-negative |
| `source_template_item_id` | `BIGINT UNSIGNED` | Nullable FK to template item |
| `is_enabled` | `BOOLEAN` | NN, default true |
| `is_user_override` | `BOOLEAN` | NN, default false |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

Goal dimension amounts are intention/tags, not an additional outflow and must not be added to the primary Needs/Emergency/Investments total. Preserve the template/version selected at plan creation.

### 23. `budgets` — category limit per calendar month

| Column | Type | Constraints / intent |
|---|---|---|
| `budget_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; tenant-safe FK |
| `category_id` | `BIGINT UNSIGNED` | NN; same-household expense category |
| `period_month` | `DATE` | NN; normalized to first day of month |
| `planned_amount` | `DECIMAL(19,4)` | NN, non-negative |
| `alert_threshold_percent` | `DECIMAL(5,2)` | NN, default 80.00; validate 0–100 (or approve threshold >100 if required) |
| `rollover_enabled` | `BOOLEAN` | NN, default false |
| `status` | `VARCHAR(20)` | NN; `ACTIVE`, `CLOSED`, `ARCHIVED` |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |
| `UNIQUE` | — | `(household_id, category_id, period_month)` for one budget per category/month in V1 |

Spent and remaining values are calculated from posted transaction splits and the approved refund/void rules. Do not persist an independently edited `spent_amount`.

---

## D. Recurring commitments and bills

### 24. `recurring_rules` — schedule definition, not a transaction

| Column | Type | Constraints / intent |
|---|---|---|
| `recurring_rule_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; tenant-safe FK |
| `rule_name` | `VARCHAR(120)` | NN |
| `event_type` | `VARCHAR(20)` | NN; `INCOME`, `EXPENSE`, `TRANSFER` |
| `expected_amount` | `DECIMAL(19,4)` | NN, greater than 0 |
| `frequency_code_id` | `BIGINT UNSIGNED` | NN, FK to `RECURRENCE_FREQUENCY` catalog |
| `interval_value` | `INT UNSIGNED` | NN, default 1; every N units |
| `day_of_month` | `TINYINT UNSIGNED` | Nullable; monthly scheduling policy required for 29–31 |
| `day_of_week` | `TINYINT UNSIGNED` | Nullable; agreed weekday numbering |
| `start_date` | `DATE` | NN |
| `end_date` | `DATE` | Nullable |
| `next_run_date` | `DATE` | Nullable; recalculable scheduling cursor, not financial fact |
| `source_account_id` | `BIGINT UNSIGNED` | Nullable; required for some event types |
| `destination_account_id` | `BIGINT UNSIGNED` | Nullable; required for transfers |
| `category_id` | `BIGINT UNSIGNED` | Nullable; required for an expense/income rule per service contract |
| `auto_create_mode` | `VARCHAR(20)` | NN; `DRAFT`, `REMINDER_ONLY` (do not silently post transactions) |
| `status` | `VARCHAR(20)` | NN; `ACTIVE`, `PAUSED`, `ENDED` |
| `created_by_user_id` | `BIGINT UNSIGNED` | NN, FK → `users` |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

### 25. `recurring_occurrences` — idempotently generated planned events

| Column | Type | Constraints / intent |
|---|---|---|
| `recurring_occurrence_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; tenant-safe FK |
| `recurring_rule_id` | `BIGINT UNSIGNED` | NN, FK to rule in same household |
| `occurrence_date` | `DATE` | NN |
| `expected_amount` | `DECIMAL(19,4)` | NN |
| `status` | `VARCHAR(20)` | NN; `PLANNED`, `SKIPPED`, `CANCELLED`, `LINKED` |
| `transaction_id` | `BIGINT UNSIGNED` | Nullable; actual occurrence transaction |
| `transfer_id` | `BIGINT UNSIGNED` | Nullable; actual transfer |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |
| `UNIQUE` | — | `(recurring_rule_id, occurrence_date)` to prevent duplicate schedule generation |

A linked occurrence references either a transaction or a transfer, not both; enforce by CHECK/service validation. Expected occurrences do not count as actual income/expense.

### 26. `bill_templates` — reusable bill definition

| Column | Type | Constraints / intent |
|---|---|---|
| `bill_template_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; tenant-safe FK |
| `bill_name` | `VARCHAR(150)` | NN |
| `expected_amount` | `DECIMAL(19,4)` | NN, non-negative |
| `category_id` | `BIGINT UNSIGNED` | Nullable same-household expense category |
| `recurring_rule_id` | `BIGINT UNSIGNED` | Nullable FK → `recurring_rules` |
| `default_account_id` | `BIGINT UNSIGNED` | Nullable same-household account |
| `reminder_days_before` | `SMALLINT UNSIGNED` | NN, default 0 |
| `is_active` | `BOOLEAN` | NN, default true |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

### 27. `bill_occurrences` — a due obligation on a specific period/date

| Column | Type | Constraints / intent |
|---|---|---|
| `bill_occurrence_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; tenant-safe FK |
| `bill_template_id` | `BIGINT UNSIGNED` | NN, FK to template in same household |
| `period_start` | `DATE` | Nullable; start of covered billing period |
| `period_end` | `DATE` | Nullable; end of covered billing period |
| `due_date` | `DATE` | NN |
| `amount_due` | `DECIMAL(19,4)` | NN, non-negative; actual bill may differ from estimate |
| `lifecycle_status` | `VARCHAR(20)` | NN; `OPEN`, `CANCELLED`; paid/partial/overdue is derived from payments and date |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |
| `UNIQUE` | — | `(bill_template_id, due_date)` unless product supports multiple bills on same due date; adjust with a stable occurrence key if needed |

### 28. `bill_payments` — one payment or partial-payment allocation against a bill

| Column | Type | Constraints / intent |
|---|---|---|
| `bill_payment_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; tenant-safe FK |
| `bill_occurrence_id` | `BIGINT UNSIGNED` | NN, FK to occurrence in same household |
| `payment_amount` | `DECIMAL(19,4)` | NN, greater than 0 |
| `payment_date` | `DATE` | NN |
| `transaction_id` | `BIGINT UNSIGNED` | Nullable; linked expense transaction |
| `transfer_id` | `BIGINT UNSIGNED` | Nullable; linked settlement/account transfer |
| `notes` | `VARCHAR(255)` | Nullable |
| `created_by_user_id` | `BIGINT UNSIGNED` | NN, FK → `users` |
| `created_at` | `DATETIME(6)` | NN |

Exactly one of `transaction_id` or `transfer_id` must be non-null. Service validates same-household linkage and aggregate successful payments do not exceed amount due unless overpayment/credit carry-forward is explicitly supported.

### 29. `credit_card_statements` — captured statement facts

| Column | Type | Constraints / intent |
|---|---|---|
| `credit_card_statement_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; tenant-safe FK |
| `account_id` | `BIGINT UNSIGNED` | NN; same-household credit-card account |
| `period_start` | `DATE` | NN |
| `period_end` | `DATE` | NN |
| `statement_date` | `DATE` | NN |
| `payment_due_date` | `DATE` | NN |
| `total_amount_due` | `DECIMAL(19,4)` | NN, non-negative |
| `minimum_amount_due` | `DECIMAL(19,4)` | NN, non-negative |
| `status` | `VARCHAR(20)` | NN; `OPEN`, `PARTIALLY_PAID`, `PAID`, `DISPUTED`, `ARCHIVED`; reconcile with linked payments before treating as authoritative |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |
| `UNIQUE` | — | `(account_id, statement_date)` |

Statement totals are snapshots of issuer data, not additional expenses. Statement line-item reconciliation/import can be added later via a child table if V1 requires it.

---

## E. Goals and operations

### 30. `goals` — target/purpose definition

| Column | Type | Constraints / intent |
|---|---|---|
| `goal_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; UQ tenant key |
| `goal_name` | `VARCHAR(150)` | NN |
| `goal_type_code` | `VARCHAR(40)` | NN; e.g. `GENERAL`, `EMERGENCY_FUND`, `EDUCATION`, `RETIREMENT`, `HOME`, `VACATION` |
| `target_amount` | `DECIMAL(19,4)` | NN, non-negative |
| `target_date` | `DATE` | Nullable |
| `target_coverage_months` | `SMALLINT UNSIGNED` | Nullable; emergency fund horizon such as 3/6/9/12 or custom |
| `priority` | `TINYINT UNSIGNED` | NN, default 3; scale documented (e.g. 1 highest) |
| `status` | `VARCHAR(20)` | NN; `ACTIVE`, `PAUSED`, `COMPLETED`, `ARCHIVED` |
| `created_by_user_id` | `BIGINT UNSIGNED` | NN, FK → `users` |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

Do not store `current_amount` as a second source of truth; derive from explicitly defined contribution/holding relations. V1 goal progress must define whether contributions are only earmarks or linked actual holdings.

### 31. `goal_contributions` — goal earmark/contribution events

| Column | Type | Constraints / intent |
|---|---|---|
| `goal_contribution_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | NN; tenant-safe FK |
| `goal_id` | `BIGINT UNSIGNED` | NN; goal in same household |
| `contribution_date` | `DATE` | NN |
| `contribution_kind` | `VARCHAR(20)` | NN; proposed `PLANNED`, `EARMARKED`, `REALIZED`; semantics to approve |
| `amount` | `DECIMAL(19,4)` | NN, greater than 0 |
| `source_account_id` | `BIGINT UNSIGNED` | Nullable; account holding earmarked money, not a cash movement by itself |
| `related_transaction_id` | `BIGINT UNSIGNED` | Nullable; links supporting real transaction without duplicating it |
| `notes` | `VARCHAR(255)` | Nullable |
| `created_by_user_id` | `BIGINT UNSIGNED` | NN, FK → `users` |
| `created_at` | `DATETIME(6)` | NN |

This table must not automatically create an expense or add value to net worth. Goal is a purpose label/earmark unless linked to a real asset/cash movement; see open decisions.

### 32. `notifications` — user delivery state

| Column | Type | Constraints / intent |
|---|---|---|
| `notification_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | Nullable FK → `households` |
| `user_id` | `BIGINT UNSIGNED` | NN, FK → `users` |
| `notification_type` | `VARCHAR(40)` | NN, stable code such as `BILL_DUE`, `BUDGET_ALERT` |
| `title` | `VARCHAR(150)` | NN |
| `message` | `VARCHAR(500)` | NN; avoid sensitive details on lock-screen by default |
| `scheduled_at` | `DATETIME(6)` | Nullable |
| `sent_at` | `DATETIME(6)` | Nullable |
| `read_at` | `DATETIME(6)` | Nullable |
| `delivery_status` | `VARCHAR(20)` | NN; `PENDING`, `SENT`, `FAILED`, `CANCELLED` |
| `deduplication_key` | `VARCHAR(160)` | Nullable; prevents duplicate generated alerts when configured with user scope |
| `created_at`, `updated_at` | `DATETIME(6)` | NN |

### 33. `audit_logs` — sensitive action trail

| Column | Type | Constraints / intent |
|---|---|---|
| `audit_log_id` | `BIGINT UNSIGNED` | PK |
| `household_id` | `BIGINT UNSIGNED` | Nullable FK → `households` |
| `actor_user_id` | `BIGINT UNSIGNED` | Nullable FK → `users`; null for system action |
| `action_code` | `VARCHAR(60)` | NN; create/update/post/void/member-permission change, etc. |
| `entity_type` | `VARCHAR(60)` | NN; domain entity name |
| `entity_id` | `VARCHAR(80)` | NN; polymorphic identifier; deliberately not a direct FK |
| `occurred_at` | `DATETIME(6)` | NN |
| `request_id` | `VARCHAR(100)` | Nullable; traces one API request |
| `metadata_json` | `JSON` | Nullable; sanitized metadata only, no secrets or unnecessary personal data |

Audit log retention, access and immutable storage policy must be defined before deployment.

---

# 4. Future tables (deferred, not V1 migrations)

| Table candidates | Purpose | Target phase |
|---|---|---|
| `sinking_funds` or dedicated sinking-fund submodel | Planned annual/irregular obligations and reserved balance behaviour | V2; decide whether `goals` can cover all semantics first |
| `investment_accounts` | Brokerage/EPF/PPF/NPS/FD/RD/investment account metadata | V2 |
| `investment_holdings` | Positions and units/valuation dates | V2 |
| `investment_transactions` | Buy/sell/contribution/withdrawal records | V2 |
| `goal_asset_allocations` | Link asset/holding amounts to goals without double-counting | V2 |
| `assets`, `liabilities` | Manually entered assets and non-account liabilities | V2 |
| `net_worth_snapshots` | Point-in-time net-worth reports | V2 |
| `tax_plans` | Tax assumptions/plans; tax year rules need own design | V2 |
| `import_jobs`, `import_rows` | CSV statement import and reconciliation | V2 |
| `credit_card_statement_lines` | Statement-line reconciliation | V2 if needed |
| `data_export_jobs`, `data_deletion_requests` | Asynchronous export/deletion workflow | Add if operational workflow requires persisted job state |
| `catalog_code_translations` | Localized labels | Later |
| `transaction_attachments` | Receipt images and file references | Later |
| `ai_insights` | Persisted AI summaries after privacy/retention policy | Later; not an authority for calculations |
| `exchange_rates` | Cross-currency transaction/reporting support | Later; V1 should reject cross-currency transfers |

## 5. Required constraints and indexes checklist

- Unique normalized user email.
- Unique membership `(household_id, user_id)`.
- Every household-owned entity scoped with `household_id` and tenant-safe relationship checks.
- Transaction/account/category/payee/member/goal references cannot cross households.
- Transaction split sum equals transaction amount for posted records.
- Account transfer source and destination differ; same-currency for V1.
- Bill payment links exactly one of transaction or transfer; partial-payment totals validated.
- Recurrence occurrence has unique `(recurring_rule_id, occurrence_date)`.
- Planned income links to at most one actual receipt; actual income is not double-counted.
- Budget unique `(household_id, category_id, period_month)`; `period_month` is first of month.
- Credit card statement unique `(account_id, statement_date)`.
- Relevant indexes: `(household_id, transaction_date)`, `(account_id, transaction_date)`, `(household_id, status)`, due date/status, parent FKs, and monthly plan/goal lookups.
- Index only actual query/access paths; every index adds write and storage cost.

## 6. Deliberately not persisted as independent truth

Avoid independently editable columns for `current_balance`, `amount_used`, `available_credit`, budget spent/remaining, goal current amount, yearly actual income, monthly actual totals and net worth. These are derived from posted transactions, account-type rules, contributions/holdings and period boundaries. If performance later requires snapshots, mark them as rebuildable, timestamped caches—not truth.
