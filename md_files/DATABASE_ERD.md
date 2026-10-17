# Database ERD — Review Draft

These Mermaid diagrams show the principal relationships, not every operational FK. They are intentionally split by domain so that each diagram remains reviewable. A rendered ERD should be generated again after table/constraint approval.

## 1. Identity, household and access

```mermaid
erDiagram
    USERS ||--o| USER_CREDENTIALS : authenticates
    USERS ||--o| USER_PREFERENCES : configures
    USERS ||--o{ HOUSEHOLDS : creates
    HOUSEHOLDS ||--o{ HOUSEHOLD_MEMBERS : has
    USERS ||--o{ HOUSEHOLD_MEMBERS : joins
    HOUSEHOLDS ||--o{ HOUSEHOLD_INVITATIONS : invites
    HOUSEHOLD_MEMBERS ||--o{ HOUSEHOLD_INVITATIONS : sends
    HOUSEHOLDS ||--o{ ACCOUNTS : owns
    HOUSEHOLD_MEMBERS o|--o{ ACCOUNTS : owns_personally

    USERS {
      bigint user_id PK
      varchar email_normalized UK
      varchar account_status
    }
    HOUSEHOLDS {
      bigint household_id PK
      bigint created_by_user_id FK
      char default_currency_code
    }
    HOUSEHOLD_MEMBERS {
      bigint household_member_id PK
      bigint household_id FK
      bigint user_id FK
      varchar role_code
      varchar status
    }
    ACCOUNTS {
      bigint account_id PK
      bigint household_id FK
      bigint account_type_code_id FK
      decimal opening_balance_amount
      varchar visibility_scope
    }
```

## 2. Actual money events

```mermaid
erDiagram
    HOUSEHOLDS ||--o{ ACCOUNTS : owns
    ACCOUNTS ||--o{ TRANSACTIONS : records
    TRANSACTIONS ||--|{ TRANSACTION_SPLITS : classified_as
    CATEGORIES ||--o{ TRANSACTION_SPLITS : categorizes
    HOUSEHOLDS ||--o{ CATEGORIES : configures
    HOUSEHOLDS ||--o{ PAYEES : configures
    PAYEES o|--o{ TRANSACTIONS : describes
    INCOME_SOURCES o|--o{ TRANSACTIONS : source_of
    HOUSEHOLDS ||--o{ ACCOUNT_TRANSFERS : owns
    ACCOUNTS ||--o{ ACCOUNT_TRANSFERS : source_or_destination
    HOUSEHOLDS ||--o{ CREDIT_CARD_STATEMENTS : captures
    ACCOUNTS ||--o{ CREDIT_CARD_STATEMENTS : statement_for
    TRANSACTIONS o|--o{ BILL_PAYMENTS : settles_bill
    ACCOUNT_TRANSFERS o|--o{ BILL_PAYMENTS : settles_bill
    BILL_OCCURRENCES ||--o{ BILL_PAYMENTS : has_payments

    TRANSACTIONS {
      bigint transaction_id PK
      bigint household_id FK
      bigint account_id FK
      varchar transaction_type
      date transaction_date
      decimal amount
      varchar status
    }
    TRANSACTION_SPLITS {
      bigint transaction_split_id PK
      bigint transaction_id FK
      bigint category_id FK
      decimal amount
    }
    ACCOUNT_TRANSFERS {
      bigint transfer_id PK
      bigint from_account_id FK
      bigint to_account_id FK
      decimal amount
      varchar status
    }
```

## 3. Yearly, monthly and allocation planning

```mermaid
erDiagram
    HOUSEHOLDS ||--o{ FINANCIAL_YEAR_PLANS : plans
    FINANCIAL_YEAR_PLANS ||--o{ MONTHLY_PLANS : contains
    MONTHLY_PLANS ||--o{ MONTHLY_PLAN_ALLOCATIONS : allocates
    ALLOCATION_TEMPLATES ||--o{ ALLOCATION_TEMPLATE_ITEMS : defines
    ALLOCATION_TEMPLATES o|--o{ MONTHLY_PLANS : selected_for
    HOUSEHOLDS ||--o{ BUDGETS : controls
    CATEGORIES ||--o{ BUDGETS : budgeted_by
    INCOME_SOURCES ||--o{ PLANNED_INCOME_ENTRIES : expected_from
    FINANCIAL_YEAR_PLANS o|--o{ PLANNED_INCOME_ENTRIES : annual_forecast
    MONTHLY_PLANS o|--o{ PLANNED_INCOME_ENTRIES : monthly_forecast
    TRANSACTIONS o|--o| PLANNED_INCOME_ENTRIES : actual_receipt

    FINANCIAL_YEAR_PLANS {
      bigint financial_year_plan_id PK
      bigint household_id FK
      date period_start
      date period_end
      int version_number
    }
    MONTHLY_PLANS {
      bigint monthly_plan_id PK
      bigint financial_year_plan_id FK
      date month_start_date
      varchar status
    }
    MONTHLY_PLAN_ALLOCATIONS {
      bigint monthly_plan_allocation_id PK
      bigint monthly_plan_id FK
      varchar allocation_group_code
      varchar allocation_code
      decimal planned_amount
      decimal planned_percentage
    }
```

## 4. Recurring commitments, bills and goals

```mermaid
erDiagram
    HOUSEHOLDS ||--o{ RECURRING_RULES : defines
    RECURRING_RULES ||--o{ RECURRING_OCCURRENCES : generates
    TRANSACTIONS o|--o{ RECURRING_OCCURRENCES : actual_transaction
    ACCOUNT_TRANSFERS o|--o{ RECURRING_OCCURRENCES : actual_transfer
    HOUSEHOLDS ||--o{ BILL_TEMPLATES : defines
    BILL_TEMPLATES ||--o{ BILL_OCCURRENCES : schedules
    BILL_OCCURRENCES ||--o{ BILL_PAYMENTS : has
    HOUSEHOLDS ||--o{ GOALS : defines
    GOALS ||--o{ GOAL_CONTRIBUTIONS : records
    ACCOUNTS o|--o{ GOAL_CONTRIBUTIONS : earmarked_account
    TRANSACTIONS o|--o{ GOAL_CONTRIBUTIONS : related_activity
    USERS ||--o{ NOTIFICATIONS : receives
    USERS ||--o{ AUDIT_LOGS : acts

    RECURRING_RULES {
      bigint recurring_rule_id PK
      bigint household_id FK
      varchar event_type
      decimal expected_amount
      date next_run_date
    }
    BILL_OCCURRENCES {
      bigint bill_occurrence_id PK
      bigint bill_template_id FK
      date due_date
      decimal amount_due
      varchar lifecycle_status
    }
    GOALS {
      bigint goal_id PK
      bigint household_id FK
      decimal target_amount
      date target_date
      varchar status
    }
```

## Notes for the reviewer

- Mermaid's compact syntax does not display every composite FK or cross-row service validation.
- Generic catalog relationships, `credit_card_details`, `user_credentials`, `user_preferences`, `audit_logs` and some operational fields are omitted from the drawing for readability; they remain in `DATABASE_DESIGN.md`.
- The detailed database dictionary is authoritative for proposed field/type names; both artifacts must be changed together when a decision changes.
