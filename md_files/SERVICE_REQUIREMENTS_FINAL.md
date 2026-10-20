# Monthly Budgeting Money Planner — Final Backend / Service Requirements

**Document type:** Backend source of truth for implementation  
**Version:** 1.0 — consolidated MVP requirements  
**Target stack:** Java 21, Spring Boot 4.x, Spring Security, Spring Data JPA, Jakarta Validation, MySQL 8.x, Flyway  
**Database schema:** `budget_db`  
**Timezone/business calendar:** `Asia/Kolkata` (IST, UTC+05:30)  
**Scope:** Individual user, monthly income, three-bucket budget, money-entry history, summary reports and manually tracked aggregate credit-card outstanding.

> This document supersedes the earlier broad 33-table draft for V1. It deliberately keeps the business schema at three tables: `users`, `monthly_budgets`, and `money_entries`. Flyway/system tables or a future framework session table are technical infrastructure, not extra business-domain tables. Do not add domain tables unless an agreed feature cannot be represented cleanly by these three tables.

## 1. Glossary and plain-language meanings

- **MVP = Minimum Viable Product.** The smallest useful, working first release that solves the app's core problem. It is not a demo with fake data, and it does not mean the final product can never grow. V1 must be reliable for the limited scope it promises.
- **Monthly income for budget:** the amount the user enters and chooses to use as the basis for that month's plan.
- **Recorded monthly income / lifetime recorded income:** the values the user entered in monthly budgets. This is not verified against a bank, payslip or payment provider.
- **Actual receipt:** money that really reached the user's bank/cash in real life. V1 does not independently record every receipt. Example: if a user enters ₹30,000 as October's monthly income, but receives ₹28,000, the app will use ₹30,000 until the user edits the still-open October budget to ₹28,000. The app must not call the original ₹30,000 a verified receipt.
- **Bucket:** Essentials, Safety, or Investments—one of the three budget allocations.
- **Money entry:** one recorded activity such as an expense, Safety saving, investment contribution, or credit-card bill payment.
- **Credit-card outstanding:** estimated combined outstanding across the user's credit cards based on the opening amount entered and the card purchases/payments recorded in this app. It is not fetched from the issuer and is not an official statement balance.

## 2. Product principles

1. Normal users must be able to understand the terms and complete common tasks quickly.
2. Backend is the financial source of truth; Angular may preview values but must not be authoritative for saving calculations.
3. Store actual user-entered events once. Derive summaries instead of storing duplicate totals.
4. Use three business tables for V1. No household, category-master, account, bill, goal, recurrence, investment-valuation, tax, or AI tables in V1.
5. Indian Rupee only. Multi-currency and FX are out of scope.
6. Historical data must not silently change when the user chooses a different preset in a later month.
7. Never equate investment contributions with investment market value or profit.
8. Never imply bank-verification or guaranteed future savings from manually entered data.

## 3. V1 features in scope

- Registration, login and logout.
- User profile: full name, email, Indian mobile number, employment type.
- One monthly budget/income snapshot per user per calendar month.
- Income type: `SALARY`, `POCKET_MONEY`, or `OTHER_INCOME`.
- Conservative, Balanced, Aggressive and Manual allocation.
- Investment toggle with the approved Option C behavior.
- Add, list, filter and void money entries.
- Date/time-aware history in IST.
- Monthly bucket progress and daily totals.
- Lifetime recorded income, lifetime consumption expenditure, Safety savings and investment-contribution summaries.
- Daily, monthly, quarterly, half-yearly, yearly and custom-date reports.
- Aggregate estimated credit-card outstanding, including an opening outstanding amount and recorded bill payments.
- Clear API errors, validation and ownership enforcement.

## 4. Explicitly out of scope for V1

No household sharing, multiple earners per month, salary receipts ledger, bank integration, account balances, individual per-card accounts/statements/due dates, credit limits, transaction imports, recurring payments, bill calendar/reminders, user-defined category management, goals, tax, EPF/PPF/NPS, net-worth/market valuation, crypto, AI financial assistant, or safe-to-spend prediction. These are future candidates, not hidden requirements for the three-table MVP.

## 5. User/profile requirements

### 5.1 `users` table

Required fields:

- `user_id` — generated primary key.
- `full_name` — trimmed, 1–120 characters.
- `email` — trim and lowercase before checking uniqueness; maximum 254 characters.
- `phone_number` — normalized Indian mobile in E.164 form, e.g. `+919876543210`. Use `+91` and 10 digits for V1. Phone is a profile value, not a login identifier.
- `employment_type` — `SALARIED`, `STUDENT`, `UNEMPLOYED`, `SELF_EMPLOYED`, or `OTHER`.
- `password_hash` — adaptive one-way password hash only; never plaintext.
- `opening_credit_card_outstanding_amount` — `DECIMAL(14,2)`, default 0.00; optional combined opening balance entered by the user at setup. This is a starting point for the aggregate outstanding calculation, not an independently updated current balance.
- `credit_card_tracking_started_at_utc` — nullable UTC timestamp. `NULL` means credit-card tracking has not been configured; when the user enables tracking, this timestamp marks when the opening baseline was taken.
- `created_at_utc`, `updated_at_utc` — UTC timestamps.
- `account_status` — `ACTIVE` or `DEACTIVATED` if account lifecycle is implemented in V1.

Unique email is required. Do not require phone uniqueness unless a future verified-phone login flow needs it. Do not return `password_hash` in any response DTO, logs or error.

### 5.2 Employment versus income source

Employment status and monthly income type are different concepts.

- `SALARIED` normally defaults the income-type selection to `SALARY`.
- `STUDENT` or `UNEMPLOYED` normally defaults it to `POCKET_MONEY`.
- `SELF_EMPLOYED` or `OTHER` must prompt the user to choose `OTHER_INCOME`, `SALARY`, or `POCKET_MONEY`; do not guess.
- Users can override the suggestion. Employment type must never silently alter a saved monthly budget.
- `income_source_label` is optional free text only when `income_type=OTHER_INCOME` (e.g. “Freelance”). It does not create multiple income sources or multiple entries.

## 6. Monthly budget model

### 6.1 `monthly_budgets` table

One row per user per calendar month. `budget_month` is the first day of the month (e.g. `2026-10-01`). Required data:

- `monthly_budget_id` primary key.
- `user_id` foreign key to `users`.
- `budget_month` date.
- `income_type` (`SALARY`, `POCKET_MONEY`, `OTHER_INCOME`).
- `income_source_label` nullable.
- `income_amount` `DECIMAL(14,2)`, minimum 0.00.
- `allocation_mode` (`CONSERVATIVE`, `BALANCED`, `AGGRESSIVE`, `MANUAL`).
- `investments_enabled` boolean.
- `essentials_percentage`, `safety_percentage`, `investments_percentage` as `DECIMAL(5,2)`.
- UTC created/updated timestamps.

Constraints: unique `(user_id, budget_month)`; month is the first day; amount is non-negative; every percentage is 0.00–100.00; allocation rules below must hold.

### 6.2 Presets

| Mode | Essentials / Needs | Emergency / Safety | Investments |
|---|---:|---:|---:|
| Conservative | 60% | 10% | 30% |
| Balanced | 55% | 10% | 35% |
| Aggressive | 50% | 10% | 40% |

These are budgeting presets, not individualized financial/investment advice.

### 6.3 Exact allocation validation

- When a preset is selected, backend loads the exact preset percentages. Client-submitted percentages cannot override a preset mode.
- If the user manually changes any percentage or chooses Manual, persist `allocation_mode=MANUAL`.
- When Investments is enabled, the three percentages must sum to exactly `100.00` and each must be within `0.00–100.00`.
- When Investments is disabled (approved Option C): `allocation_mode=MANUAL`, `investments_percentage=0.00`, and `essentials_percentage + safety_percentage = 100.00`. Ask the user to set Essentials and Safety. Do not auto-redistribute the former investment percentage.
- When creating/saving a budget with Investments OFF, reject it if the target month already has any POSTED `INVESTMENT` entry, including when the budget is being created for the first time after investment entries were recorded. When switching Investments OFF, reject the change if the target month already has any POSTED `INVESTMENT` entry. Return a clear conflict error and retain the existing budget. Do not delete or rewrite old entries. If there are no posted investment entries, accept the change after validating the two manual percentages.
- While a month's Investments toggle is OFF, reject new `INVESTMENT` entries for that month, even if requests bypass the Angular UI.
- When switching Investments ON again, require the user to explicitly choose a preset or enter all three percentages. Do not silently restore stale percentages.
- Preset percentages are immutable code-defined product rules for V1; save chosen percentages on each month so later application releases do not alter history.

### 6.4 Monthly edit policy

- Current IST month: budget is editable.
- Future months: may be created/edited as a plan. They are excluded from lifetime-to-date recorded-income totals until their month begins.
- Past months: read-only after the month ends. Do not accept budget changes or new/backdated money entries for a past month through ordinary APIs. Return a stable conflict code such as `PERIOD_READ_ONLY`.
- The service computes the current month in `Asia/Kolkata`, not from server default timezone.
- Never make a past month editable just because the browser submits a different date.

### 6.5 Zero or missing income

- Income may be 0.00 but cannot be negative.
- Missing monthly budget returns `BUDGET_NOT_SET`, not a fake budget with 0.00 income.
- If income is 0.00, month summary status is `NO_INCOME`; never show a normal green `WITHIN_ALLOCATION` state as if financial capacity existed.
- Money activity can still be shown for a no-income/missing-budget period, but the UI must disclose that the budget comparison cannot be interpreted normally.

## 7. Money entry and accounting model

### 7.1 `money_entries` table

One row represents one event, with one amount and one category code. Required fields:

- `money_entry_id` generated primary key.
- `user_id` FK.
- `entry_at_utc` `DATETIME(6)`, stored in UTC.
- `entry_type`.
- `bucket`, nullable only for credit-card bill payment.
- `category_code`, stable machine code, not an arbitrary display label.
- `amount` `DECIMAL(14,2)`, strictly greater than 0.00.
- `payment_method`; required for `EXPENSE` so credit-card purchases can be tracked consistently. Optional for Safety saving/Investment when not relevant. For `CREDIT_CARD_PAYMENT`, record the method used to settle the bill (`UPI`, `CASH`, `DEBIT_CARD`, `BANK_TRANSFER`, `WALLET`, `OTHER`) and reject `CREDIT_CARD`. Allowed codes overall: `UPI`, `CASH`, `CREDIT_CARD`, `DEBIT_CARD`, `BANK_TRANSFER`, `WALLET`, `OTHER`.
- `note` optional, max 500 characters.
- `status`: `POSTED` or `VOID`.
- optional `client_request_id` (UUID/idempotency key) to prevent duplicate submissions on retry.
- `voided_at_utc`, `void_reason` nullable.
- UTC created/updated timestamps.

Index requirements: `(user_id, entry_at_utc)`, `(user_id, entry_type, entry_at_utc)`, `(user_id, bucket, entry_at_utc)`, and a unique `(user_id, client_request_id)` where the nullable/idempotency design permits it.

### 7.2 Supported entry types and accounting

| Type | Bucket | Impact on consumption expenditure | Impact on bucket use | Credit-card outstanding |
|---|---|---|---|---|
| `EXPENSE` | `ESSENTIALS` or `SAFETY` | Adds expense | Adds to that bucket's use | If payment method is `CREDIT_CARD`, increases outstanding |
| `SAFETY_SAVING` | `SAFETY` | Not consumption expenditure | Adds to Safety allocation use | No impact |
| `INVESTMENT` | `INVESTMENTS` | Not consumption expenditure | Adds to Investment allocation use | Credit-card payment method is rejected in V1 |
| `CREDIT_CARD_PAYMENT` | NULL | Not consumption expenditure | No bucket use | Reduces outstanding |

`CREDIT_CARD_PAYMENT`'s `payment_method` describes how the user paid the bill (e.g. UPI or bank transfer), not `CREDIT_CARD`. Do not count a bill payment as a second expense. Card purchases are the expense; repayment is settlement.

### 7.3 Credit-card outstanding — simple V1 definition

V1 tracks one combined estimate across all of a user's cards. It does not track each card separately, official statement balance, due date, limit or minimum due.

When tracking is configured:

`current_outstanding = opening_outstanding + tracked_posted_card_purchases - tracked_posted_card_payments`

Only card purchases and payments with `entry_at_utc >= credit_card_tracking_started_at_utc` affect the formula. The opening amount represents the outstanding balance at the moment tracking began; entries dated before that moment can still appear in expense history, but must not be added to or subtracted from the opening balance again. This avoids double counting when a user backfills old purchases after starting the app.

Where:

- `opening_outstanding` is the combined amount entered when the user enables tracking. `credit_card_tracking_started_at_utc=NULL` means tracking is not configured.
- A posted `EXPENSE` with `payment_method=CREDIT_CARD` increases outstanding, regardless of which expense bucket it uses; reject it until tracking is configured.
- A posted `CREDIT_CARD_PAYMENT` reduces outstanding and is not an expense; reject bill-payment records until tracking is configured and reject a payment timestamp before the tracking start.
- `VOID` entries do not count. Voiding a card purchase reduces the calculated outstanding; voiding a card payment increases it again.
- Reject a card payment greater than the outstanding available immediately before that payment, unless a separate credit-balance/refund feature is later approved.
- The opening baseline can be corrected only while no posted card purchase/payment exists at or after the tracking-start time. After tracked events exist, a correction requires a dedicated adjustment flow, deferred from V1.
- Label it **Estimated credit-card outstanding (manual tracking)** in the UI. Explain it depends on all relevant purchases/payments being entered and is not synchronized with the issuer.

### 7.4 Stable category codes (no category table in V1)

Backend owns an allow-list and exposes it by API. Angular must submit codes, not labels. Starting allow-list:

- Essentials: `GROCERIES`, `RENT`, `UTILITIES`, `TRANSPORT`, `FOOD_DINING`, `MEDICAL`, `EDUCATION`, `SHOPPING`, `SUBSCRIPTIONS`, `OTHER_EXPENSE`.
- Safety: `EMERGENCY_FUND`, `SAFETY_SAVING`, plus approved safety expense categories such as `MEDICAL` and `OTHER_EXPENSE`.
- Investments: `SIP`, `FD`, `STOCKS`, `GOLD_SIP`, `OTHER_INVESTMENT`.
- Credit-card payment: `CREDIT_CARD_BILL_PAYMENT`.

The category response includes the code, user-visible label, allowed entry types and allowed bucket(s). Backend rejects unknown codes or invalid combinations. Category list changes require a code change/release until custom categories are deliberately scoped. Labels may change without changing stored codes. An `EXPENSE` must include a payment method; this is necessary for tracking credit-card purchases without guessing.

### 7.5 Entry lifecycle and edits

- A newly saved entry is `POSTED` only after all validation succeeds.
- Posted entries are not physically deleted or ordinarily edited. Correct an entry by voiding it and creating a replacement. V1 does not support restore-from-void.
- Void action records timestamp and optional reason; voided records remain queryable in an audit/history view but are excluded from active totals.
- An entry's `entryDate` must fall in the current IST month or a future month is not allowed for actual activity; actual money entries cannot be future dated. Past-month entries are read-only under the approved period policy.
- Only `EXPENSE` entries may use `payment_method=CREDIT_CARD`. Do not allow card payments or investment contributions to be submitted as card purchases unless that specific accounting behavior is later designed.

## 8. Date, time and reporting contract

- Business timezone: `Asia/Kolkata`, which is IST (UTC+05:30). Do not use the server's or browser's implicit timezone for business rules.
- For create-entry API requests, use `entryDate` (`YYYY-MM-DD`) and `entryTime` (`HH:mm`) interpreted as IST. Backend combines them with `ZoneId.of("Asia/Kolkata")` and stores the equivalent UTC instant in `entry_at_utc`.
- API responses return an ISO-8601 timestamp with offset or explicit `entryDateIst` and `entryTimeIst` fields. Never return a naive ambiguous datetime.
- Report queries accept `fromDate` and `toDate` as IST calendar dates. Convert the start of `fromDate` and the start of the day after `toDate` in `Asia/Kolkata` to UTC, then query `[startUtc, endExclusiveUtc)`. Do not use a timestamp end of `23:59:59.999`.
- Default reporting year: Indian Financial Year (1 April–31 March). FY quarters: Q1 Apr–Jun, Q2 Jul–Sep, Q3 Oct–Dec, Q4 Jan–Mar; half-years H1 Apr–Sep and H2 Oct–Mar. Use actual date boundaries rather than hard-coding year strings.
- A transaction belongs to a period by its IST event date. Monthly recorded income belongs to `budget_month`, not the daily entry date.
- For a daily/custom partial-month report, do not pretend that the whole month's recorded income happened on a single day. Either display income only when the report includes whole budget months, or omit the income metric and explain “Income is recorded monthly; choose a full-month period to include it.” Expense/savings/investments are filtered by entry dates.

## 9. Monetary calculations and exact rounding

- Persist INR amounts as `DECIMAL(14,2)`. Use Java `BigDecimal` for all money and percentage calculations. Never use `double`/`float` for authoritative monetary values.
- Accept incoming amounts as decimal strings in DTOs (e.g. `"30000.00"`) or parse JSON decimal values directly into `BigDecimal`; reject precision beyond 2 decimal places rather than silently changing the user's input.
- Allocation percentage values support 2 decimal places and must sum exactly to `100.00` at percentage precision.
- Deterministic allocation rounding: calculate Essentials amount first using `income × essentials% / 100`, rounded to scale 2 with `RoundingMode.HALF_UP`; calculate Safety amount similarly when it is not the residual; calculate the final enabled bucket as `income - alreadyRoundedOtherBucketAmounts` so the displayed bucket amounts add exactly to income. Choose the final bucket as Investments when enabled. When Investments is off, set Investments amount to 0.00, calculate Essentials with HALF_UP and Safety as `income - Essentials`.
- Do not independently round all three amounts and allow the total to differ by ₹0.01. The residual adjustment is the authoritative rule.
- API returns amounts as decimal strings with exactly two fractional digits; frontend formats for display but does not recompute the source-of-truth amount.
- Always test edge values, e.g. ₹0.01, ₹1.00, ₹10,000.01, very small percentages, 100% allocation, and Investments OFF.

## 10. Summary formulas and status definitions

- `recordedIncome` over a report = sum of `income_amount` for recorded monthly budget rows whose budget month belongs to the chosen whole-month period and is not in the future. Future budget plans do not count as lifetime-to-date income.
- For full-period summaries, lifetime recorded income = sum monthly budget incomes from the first recorded budget month through the current month (distinct row per month).
- `consumptionExpenditure` = sum `POSTED` `EXPENSE` entries only.
- `safetySavings` = sum `POSTED` `SAFETY_SAVING` entries.
- `investmentContributions` = sum `POSTED` `INVESTMENT` entries.
- `creditCardOutstanding` uses the formula in §7.3.
- `bucketUsed`: Essentials = posted Essentials expenses; Safety = posted Safety expenses + posted Safety savings; Investments = posted investment contributions. Credit-card bill payments never count toward a bucket.
- `bucketRemaining = bucketLimit - bucketUsed`; it may be negative. Do not clamp negative balances to zero because overspending must remain visible.
- Month status: `BUDGET_NOT_SET`, `NO_INCOME`, `WITHIN_ALLOCATION`, `OVER_ALLOCATION`. If no budget exists, `BUDGET_NOT_SET`. If income=0, `NO_INCOME` and show actual activity plus any overshoot separately. Otherwise, if any bucket exceeds its limit, `OVER_ALLOCATION`; else `WITHIN_ALLOCATION`.
- Entry list empty is a separate UI state, not a financial status.
- V1 does not claim “Safe to Spend today,” predict month-end savings, or make investment advice claims. It reports observed facts and current allocation progress only.

## 11. API contract (versioned under `/api/v1`)

Use DTOs; never serialize JPA entities directly. `userId` is derived from the authenticated principal and is not accepted from client bodies/query strings as an ownership authority.

### Authentication/profile

- `POST /api/v1/auth/register` — full name, email, phone, employment type, password.
- `POST /api/v1/auth/login` — email/password; generic invalid-credentials error.
- `POST /api/v1/auth/logout` — invalidate the authenticated session.
- `GET /api/v1/auth/csrf` — initialize/return CSRF token as required by chosen Spring Security configuration.
- `GET /api/v1/me` — safe profile DTO (never password hash).
- `PATCH /api/v1/me` — update editable profile fields with validation. Do not allow editing opening card outstanding after card activity exists.

### Budget

- `GET /api/v1/budgets/{yyyy-MM}` — return saved monthly budget. If absent, return a documented `BUDGET_NOT_SET` response that Angular renders as an onboarding/setup state.
- `PUT /api/v1/budgets/{yyyy-MM}` — create/update the current/future month's budget idempotently; past months reject with `409 PERIOD_READ_ONLY`.
- For a preset request, server sets exact percentages. For manual allocation, server validates totals and investment-toggle rules.

### Money entries

- `POST /api/v1/money-entries` — create one entry; server assigns user ID, validates date/bucket/category/accounting rules, and supports an idempotency request ID.
- `GET /api/v1/money-entries?fromDate=&toDate=&entryType=&bucket=&categoryCode=&status=&page=&size=&sort=` — paginated history, date filter interpreted in IST. Default page size 20; server cap 100; default sort newest first.
- `GET /api/v1/money-entries/{id}` — own entry only.
- `POST /api/v1/money-entries/{id}/void` — void own posted entry; optional reason; rejects voiding twice.

### Category reference data and reports

- `GET /api/v1/reference-data/categories?entryType=&bucket=` — return stable codes, labels and allowed combinations.
- `GET /api/v1/reports/monthly-summary?month=YYYY-MM` — income, allocation limits, used/remaining amounts, separate expense/saving/investment totals, credit-card outstanding and status.
- `GET /api/v1/reports/summary?fromDate=&toDate=&groupBy=DAY|MONTH|QUARTER|HALF_YEAR|YEAR` — aggregate time-series. Use pagination/aggregation in database; never download all entries just to total them in Angular.
- `GET /api/v1/credit-card/summary` — current estimated aggregate outstanding, opening baseline and explanation that this is manually tracked.
- `PUT /api/v1/credit-card/opening-outstanding` — enable tracking and set the opening amount/as-of time, or correct the baseline before any posted card purchase/payment exists at or after the tracking-start time; reject afterwards.

API names may be refined during controller implementation, but UI and service must agree on one versioned contract before Angular integration.

## 12. Request/response validation and errors

Use Jakarta Bean Validation for shape/basic constraints and service-level validation for cross-field/business rules. Revalidate all financial invariants in the backend even if Angular already validated them.

Standard error payload shape:

```json
{
  "timestamp": "2026-10-18T12:00:00Z",
  "status": 400,
  "code": "ALLOCATION_TOTAL_INVALID",
  "message": "Essentials and Safety must total 100% when Investments are disabled.",
  "fieldErrors": [
    { "field": "safetyPercentage", "code": "TOTAL_MUST_EQUAL_100", "message": "Adjust the percentages so the total is 100%." }
  ],
  "traceId": "server-generated-correlation-id"
}
```

- `400`: malformed request or field validation failure.
- `401`: not authenticated / session expired.
- `403`: authenticated but action forbidden.
- `404`: own resource not found; do not leak whether another user's resource exists.
- `409`: duplicate monthly budget conflict, period read-only, investment toggle blocked by recorded entries, outstanding payment exceeds current balance, or invalid lifecycle transition.
- `429`: authentication rate-limited.
- `500`: generic message plus correlation ID; no SQL, stack trace, secrets or personal data exposed.

Never log passwords, session IDs, CSRF tokens, password hashes or full financial payloads. Logs may include safe IDs/correlation IDs and event code.

## 13. Security requirements

### 13.1 Authentication model

For the Angular app and Spring Boot service controlled by the same project, use Spring Security server-side session authentication with an HttpOnly cookie as the initial recommended design. Do not put session or bearer tokens in `localStorage`.

- Cookie: HttpOnly; Secure in HTTPS production; SameSite=Lax or stricter where compatible; narrowly scoped path/domain.
- CSRF protection enabled for state-changing requests.
- In local development, use an Angular `/api` dev proxy to Spring Boot where possible so requests are same-origin from the browser. If direct cross-origin CORS is used, allow only exact configured origins and credentials; never combine credentialed CORS with wildcard `*`.
- Keep framework defaults for session ID rotation after login and session expiration; invalidate session at logout.
- In-memory sessions are acceptable for local development. Before production/multiple backend instances, define a persistent server-side session store (e.g. Spring Session JDBC/Redis) and protect the session store. Technical framework tables may be required; do not count them as product domain tables.

### 13.2 Passwords and account controls

- Use Spring Security `PasswordEncoder`/`DelegatingPasswordEncoder` with a supported adaptive encoder (BCrypt or Argon2) and benchmark/tune settings; never use `NoOpPasswordEncoder`, plain SHA-256 or reversible encryption for passwords.
- Set reasonable password minimum (recommend 12 characters), maximum to defend resource exhaustion (e.g. 128 characters), allow password managers and paste.
- Normalize email on registration/login, enforce uniqueness atomically, and return generic login failure.
- Rate-limit repeated login attempts and avoid account enumeration in public errors.
- Before public production, implement secure password reset/email ownership verification and explicit account data deletion/export policy. During local-only development these can be staged, but must be release gates.

### 13.3 Authorization and privacy

- Resolve the user's identity from `SecurityContext`/authenticated principal on every protected request.
- Query and mutate records with both the authenticated user's ID and the target resource ID, e.g. repository method `findByUserIdAnd...`; do not fetch by ID and assume the frontend protects ownership.
- Test User A cannot read, update or void User B's budgets/entries even when guessing IDs.
- Use least-privilege DB credentials; app runtime should not need schema-alter/drop permission once migrations are applied. Keep DB passwords in environment variables/secret store, never committed in `application.properties`.
- HTTPS required outside localhost development. Configure exact CORS origin(s).

## 14. Backend architecture requirements

Keep the codebase simple but layered:

- `controller` — HTTP contract and DTO binding; no business calculations.
- `dto` — request/response types and validation annotations.
- `service` — allocation engine, budget lifecycle, entry accounting, card outstanding calculation and report aggregation.
- `repository` — JPA queries constrained by authenticated user ID and period.
- `entity` — only three domain entities initially.
- `config` — Spring Security, CORS/CSRF, time zone and application configuration.
- `exception` — domain exceptions + global `@ControllerAdvice` mapping to standard error contract.

Do not build a generic event framework, generic catalog schema, mapper framework or microservices for this MVP. Use focused methods with unit tests.

## 15. Database / migration requirements

- Verify actual MySQL server version. Use MySQL 8.0.16 or newer if relying on enforced CHECK constraints; prefer a currently supported MySQL release.
- Add `org.flywaydb:flyway-mysql` compatible with the Spring Boot-managed Flyway version; MySQL driver support is a separate Flyway dependency in current Flyway releases.
- Create versioned migration `V1__create_core_budgeting_tables.sql` under `src/main/resources/db/migration/`.
- Keep `spring.jpa.hibernate.ddl-auto=none` until entities/migration are set; after mappings exist, prefer `validate` to detect mapping/schema drift. Do not use `create`, `create-drop` or Hibernate auto-update in normal runs. Set the MySQL connection/session timezone to UTC (for example with `spring.datasource.hikari.connection-init-sql=SET time_zone = '+00:00'`, if supported by the selected connector/configuration) because `DATETIME(6)` does not carry timezone metadata. Application timestamps must be converted explicitly to/from UTC.
- Enable Flyway deliberately only after reviewing the migration and verifying `budget_db` contents. Run first against a disposable empty schema/test database; then run against the intended dev schema after confirmation.
- Never use `flyway clean` on `budget_db` as a troubleshooting shortcut.
- Migration must create only the three business tables; index and FK definitions must match this document. Flyway adds `flyway_schema_history` as a technical metadata table.
- Save SQL and code in Git. Once a versioned migration has been applied/shared, do not rewrite it to change history; add a new forward migration.
- Do not commit credentials. Use `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` environment variables. Ensure `opencode.json` and local secrets are ignored.

## 16. Automated test requirements

### Allocation/budget unit tests

- Conservative 60/10/30, Balanced 55/10/35 and Aggressive 50/10/40 exact defaults.
- Attempt to submit wrong percentages with a preset is ignored/rejected; result stays exact preset.
- Manual ON requires exactly 100.00 total.
- Investments OFF requires MANUAL, 0 investment, Essentials+Safety=100.00.
- Switching OFF with a POSTED investment entry returns 409 and does not change the budget.
- New investment entry is rejected while its month's toggle is OFF.
- Current month editable, future month editable, past month rejects changes.
- Zero income produces `NO_INCOME`; absent budget produces `BUDGET_NOT_SET`.

### Money and rounding tests

- Income ₹0.01, ₹1.00, ₹10,000.01 and large valid INR amounts.
- Derived bucket rupee values always sum exactly to income (or income+? no—the final amounts must equal income exactly).
- Entry amount zero/negative/over-precision rejected.
- Entry type/bucket/category/payment-method mismatches rejected.
- POSTED entries count, VOID entries do not.
- Void a card purchase reduces outstanding; void a card payment increases outstanding.
- Card purchase counted once as expenditure; bill payment is not another expense.
- Card payment above current outstanding rejected; opening baseline cannot change after card events.
- Idempotent retry does not create duplicate entries.

### Reporting/security/integration tests

- IST date boundary and report range around midnight, month-end, quarter boundaries, New Year and 1 April FY boundary.
- Income counted once per recorded month; future budgets excluded from lifetime-to-date income.
- Aggregates for daily/monthly/FY quarter/FY half/year/custom range match seeded examples.
- User A cannot read/modify/void User B data.
- CSRF is enforced for unsafe authenticated browser requests; logout invalidates the session.
- Flyway creates expected schema on an empty disposable MySQL schema; no manual DDL drift.
- Integration tests should run against MySQL of the same major version (Testcontainers or isolated test schema), not rely solely on H2 behavior.

## 17. Definition of done for service

A service feature is complete only when:

1. DTO/request/response contract is documented.
2. Authentication/ownership is enforced where required.
3. Server validation is implemented.
4. Financial calculation runs in Java `BigDecimal`.
5. Database constraints/indexes match the domain rule.
6. Error cases return stable codes and do not leak secrets/data.
7. Unit and relevant integration tests pass.
8. `mvnw.cmd clean test` succeeds.
9. Migration has been tested against a disposable MySQL schema.
10. Developer report lists changed files, tests run, results and known limitations.

## 18. Recommended implementation order

1. Inspect MySQL version and confirm `budget_db` has no application tables.
2. Review/finalize the V1 migration including credit-card opening balance/payment handling.
3. Add Spring Security and correct Flyway MySQL module; configure session/CSRF and safe environment variables.
4. Implement registration/login/logout and `users` repository/service/controller.
5. Implement budget allocation engine and monthly budget APIs.
6. Implement money-entry category rules, create/list/void flows and card outstanding calculation.
7. Implement reporting queries with IST period boundaries and pagination.
8. Build Angular workflows against the agreed DTO contract.
9. Run automated acceptance tests, then apply migration to the intended development DB.

## 19. Technical references

- Spring Security password storage: https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
- Angular HTTP/XSRF: https://angular.dev/best-practices/security and https://angular.dev/guide/http/setup
- OWASP REST security (HTTPS, endpoint access control, CORS): https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html
- OWASP session management: https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
- MySQL CHECK constraint enforcement: https://dev.mysql.com/doc/refman/8.0/en/constraints.html
- Flyway MySQL dependency: https://documentation.red-gate.com/fd/reference/database-driver-reference/mysql
