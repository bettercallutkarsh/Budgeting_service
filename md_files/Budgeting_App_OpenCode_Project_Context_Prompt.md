# OpenCode Project Context & Implementation Prompt — Monthly Budgeting Money Planner

> **How to use:** Open OpenCode with workspace root `D:\Budgeting_App` and paste this entire prompt as the first project instruction. Keep the backend and frontend as separate Git repositories. This prompt gives context and implementation guardrails; always inspect the current local files before acting.

---

## 1. Your role and how to work

Act as a senior full-stack engineer, database designer, application security reviewer, and pragmatic technical lead. You are helping build a real, beginner-maintainable personal budgeting application—not a demo that merely compiles.

Use critical thinking. Find requirement conflicts and technical gaps before coding. Explain important choices in simple Hinglish when reporting to the user, but keep code, identifiers, API contracts, and technical documents professional and consistent.

Work incrementally. Do not generate the whole product in one huge change. For every stage:

1. Inspect the existing files, `git status`, current migrations, and configuration first.
2. State the exact scope and likely files to change.
3. Implement the smallest coherent change.
4. Run relevant tests/builds and inspect the results.
5. Report changed files, commands/tests and their outcome, unresolved risks, and the next step.

Never claim tests passed or a migration succeeded unless you ran the command and observed success. If a command fails, stop, diagnose it, and report the exact non-secret error. Prefer simple code over generic frameworks, microservices, or unnecessary abstractions.

## 2. Workspace and repository boundaries

Workspace root:

`D:\Budgeting_App`

Backend:

`D:\Budgeting_App\Budgenting_backend`

Frontend:

`D:\Budgeting_App\Budgenting_ui`

These are **two separate Git repositories**. Do not initialize a third Git repository at `D:\Budgeting_App`, do not move files between repositories without reason, and do not rewrite existing Git history or force-push. Before editing, run `git status --short --branch` inside both repositories and preserve the user's uncommitted changes.

Remote repositories already created:

- Backend: `https://github.com/bettercallutkarsh/Budgeting_service.git`
- Frontend: `https://github.com/bettercallutkarsh/BudgetingApp_ui.git`

Both repositories use the `master` branch convention. Do not commit, push, or change remotes unless specifically requested. Do not commit secrets.

## 3. Current technical environment (verify, don't blindly assume)

### Backend

- Java: Eclipse Temurin JDK 21.
- Spring Boot: `4.1.1`.
- Build: Maven Wrapper (`mvnw.cmd`); Maven Wrapper was confirmed working.
- Database: MySQL server reported as `26.7`.
- Database name: `budget_db`.
- Test schema: `budget_db_migration_test`.
- Current Flyway artifacts resolved successfully: `spring-boot-starter-flyway 4.1.1`, `flyway-mysql 12.4.0`, `flyway-core 12.4.0`.
- `spring.jpa.hibernate.ddl-auto=none` is intentional. Do not replace it with `create`, `create-drop`, or `update`. After entities and migrations are aligned, `validate` may be considered deliberately, with tests.
- `application.properties` previously had `spring.flyway.enabled=false`; check the current file and current-process environment because environment variables were used during local migration runs.
- Environment variables `DB_USERNAME` and `DB_PASSWORD` were saved at Windows CurrentUser scope. Never print them, request them in chat, place them in source, or include them in logs. `DB_URL` is assigned per terminal when choosing a target database.
- Flyway logged a compatibility warning: MySQL `26.7` is newer than Flyway `12.4.0`'s documented verified version (the runtime log said latest verified was MySQL `9.4`). V1 worked locally, but do not treat the version pairing as officially verified; test every migration in the disposable schema before applying it to `budget_db`.

### Frontend

- Angular CLI and Angular: `22.2.3`.
- Node.js: `24.22.0`.
- npm: `11.19.1`.
- Existing Angular workspace at `D:\Budgeting_App\Budgenting_ui` starts with `npm run start` once the PowerShell execution-policy issue was resolved. Local development URL is `http://localhost:4200/`.
- Angular currently contains the generated starter UI; the actual budgeting workflow/dashboard is still to be built.
- Do not store passwords, session identifiers, bearer tokens, database credentials, or provider API keys in Angular source or `localStorage`.

## 4. Current confirmed database state

The following was confirmed in DBeaver:

- `budget_db` has the business tables `users`, `monthly_budgets`, `money_entries` and Flyway's technical metadata table `flyway_schema_history`.
- The same V1 migration was tested first on `budget_db_migration_test` and then applied to `budget_db` successfully.
- The V1 migration file is at:

`D:\Budgeting_App\Budgenting_backend\src\main\resources\db\migration\V1__create_core_budgeting_tables.sql`

- `V1__create_core_budgeting_tables.sql` is already applied in both schemas. **Never modify V1 after it has been applied.** Use a new forward migration such as `V2__...sql` for all schema corrections.
- The test schema already contains V1. Apply V2 there as the migration test; do not assume it is empty, and do not drop/recreate it.
- The database structure visually inspected in DBeaver included these columns (inspect actual migration/source for full definitions before coding):
  - `users`: `user_id`, `full_name`, `email`, `phone_number`, `employment_type`, `password_hash`, `opening_credit_card_outstanding_amount`, `credit_card_tracking_started_at_utc`, `account_status`, `created_at_utc`, `updated_at_utc`.
  - `monthly_budgets`: `monthly_budget_id`, `user_id`, `budget_month`, `income_type`, `income_source_label`, `income_amount`, `allocation_mode`, `investments_enabled`, `essentials_percentage`, `safety_percentage`, `investments_percentage`, `created_at_utc`, `updated_at_utc`.
  - `money_entries`: `money_entry_id`, `user_id`, `entry_at_utc`, `entry_type`, `bucket`, `category_code`, `amount`, `payment_method`, `note`, `status`, `client_request_id`, `voided_at_utc`, `void_reason`, `created_at_utc`, `updated_at_utc`.

Column details, actual check/unique/index names, foreign keys, and allowed values must be read from V1 SQL, `SHOW CREATE TABLE`, and schema history—not inferred only from these notes/screenshots.

## 5. Product vision and intentionally small V1 scope

This is a simple, friendly Indian personal budgeting app that a non-technical person can use. Keep V1 small. Do not silently restore the old 33-table design. The current business schema is intentionally limited to three domain tables:

1. `users`
2. `monthly_budgets`
3. `money_entries`

`flyway_schema_history` is Flyway metadata, not a business table.

Core user journey:

1. Register and log in.
2. Enter/adjust the current month's income and income type.
3. Select Conservative, Balanced, Aggressive, or Manual allocation.
4. Add daily expenses, safety savings, investment contributions, and tracked credit-card payments.
5. See monthly allocation progress, recent history, and period reports.

Keep advanced features out of V1 unless the product owner explicitly changes scope: household sharing/invitations, multiple bank accounts, complex account transfers, bill calendars/recurrence, goals, tax planning, net-worth/portfolio valuation, bank integrations, CSV imports, AI financial advice, and Safe-to-Spend projections. Do not add tables merely to be “future ready.” Propose any scope expansion and explain the benefit and cost before implementing it.

## 6. Income and historical month rules

- A monthly budget's `income_amount` means user-entered **Recorded Income for budgeting**, not verified bank receipt. No bank integration exists.
- Employment types should support the agreed cases, including salaried users, students/unemployed users using pocket money, and `OTHER` income if allowed by the current final requirements. Inspect actual allowed values before enforcing enums. Do not equate `employment_type` and `income_type` without checking the contract.
- Each user can have only one monthly budget per calendar month. Verify the unique key in V1 and enforce duplicate conflict behavior in the service.
- Current month can be edited under approved rules. Past months are read-only; reject attempts to update a past budget with a consistent `409 PERIOD_READ_ONLY` response. Future budgets may be created if the final requirements specify it, but future income must not count in lifetime-to-date Recorded Income.
- Lifetime-to-date Recorded Income should aggregate each eligible recorded month once and must exclude future budgets. Label it “Recorded Income” (not “verified earnings”).
- If no budget exists for a month, expense entry should still be possible if the requirements allow, but the UI/API must clearly report `BUDGET_NOT_SET`; do not silently create a zero-income budget.
- If recorded income is zero, show `NO_INCOME`; do not label zero spending as ordinary `WITHIN_ALLOCATION`.

## 7. Allocation rules — backend is the source of truth

Default presets:

| Mode | Essentials / Needs | Emergency / Safety | Investments |
|---|---:|---:|---:|
| `CONSERVATIVE` | 60% | 10% | 30% |
| `BALANCED` | 55% | 10% | 35% |
| `AGGRESSIVE` | 50% | 10% | 40% |

Rules:

- Selecting a preset must set the exact preset percentages. A request that submits a preset name with different percentages must not be silently accepted.
- When a user edits the percentages manually, store mode as `MANUAL`.
- If Investments are enabled, all three percentages must total exactly 100% (under the decimal policy).
- When Investments are OFF, user manually sets Essentials and Safety; Investments must be 0%, and Essentials + Safety must total exactly 100%. Do not automatically redistribute the former investment percentage.
- If a posted investment entry already exists for the month, block switching Investments OFF with a clear conflict reason. When Investments are OFF, reject new investment entries for that month in the backend too.
- Use Java `BigDecimal`, never `double` or JavaScript floating-point arithmetic as the source of financial truth. Money inputs/postings are INR with two minor units. Use a deterministic rounding/residual rule so bucket amount totals exactly equal the recorded income; test paise edge cases.
- Allocation presets are planning templates, not universal financial advice.

## 8. Money-entry types, buckets, and categories

Allowed core entry types:

- `EXPENSE`
- `SAFETY_SAVING`
- `INVESTMENT`
- `CREDIT_CARD_PAYMENT`

Bucket rules:

- `EXPENSE`: bucket is mandatory and must be `ESSENTIALS` or `SAFETY`.
- `SAFETY_SAVING`: bucket must be `SAFETY`.
- `INVESTMENT`: bucket must be `INVESTMENTS`.
- `CREDIT_CARD_PAYMENT`: bucket must be `NULL`.

Category/bucket/type compatibility must be checked in the backend. Start with stable, backend-owned category codes, such as `GROCERIES`, `RENT`, `UTILITIES`, `MEDICAL`, `TRANSPORT`, `SIP`, `FD`, `STOCKS`, and `GOLD_SIP`. Use a small predefined category list for V1; no extra category table is required unless a reviewed requirement justifies it. Do not treat arbitrary user-supplied category text as a trusted code.

Money-entry rules:

- Amount must be positive and within a documented maximum.
- Reject malformed date/time, invalid entry type/category/bucket/payment method, unsupported status transitions, and new investments when the month's investment setting is OFF.
- Only posted entries count in reports. Void entries remain for audit/history but do not count in totals. Voiding twice must return a stable conflict response.
- Support `client_request_id` idempotency: retrying the same request ID by the same user must not create a duplicate entry; define conflicting payload behavior and test it.
- List endpoints must be paginated, capped (default size 20, max 100 unless updated by approved contract), sorted deterministically, and always scoped to authenticated user.

## 9. Credit-card behavior (simple V1 tracking, not a bank integration)

- V1 tracks one aggregate manually entered opening credit-card outstanding baseline for the user, dated by `credit_card_tracking_started_at_utc`; this is not per-card or issuer-synced.
- A credit-card purchase is one `EXPENSE`. It contributes to consumption expenditure once and also increases the estimated outstanding when it is a tracked credit-card purchase after the tracking start.
- A `CREDIT_CARD_PAYMENT` reduces estimated outstanding, but must **not** increase expense totals a second time.
- Do not allow a repayment larger than the tracked current outstanding unless an explicit, reviewed overpayment rule is added.
- Report this value as **Estimated credit-card outstanding**, not live/verified issuer balance.
- Be explicit in DTOs and tests about which payment method codes count as credit-card purchases/payments. Never infer actual issuer data.

## 10. Timezone and date contract

- Persist event instants consistently in UTC. MySQL `DATETIME(6)` itself does not contain timezone metadata; database session timezone and Java conversions must be explicit.
- Use deliberate Java types (`Instant` for a point in time; `LocalDate` for calendar dates such as `budget_month`). Do not treat `DATETIME` as timezone-aware.
- Configure MySQL session/JDBC handling for UTC, for example with a supported connection-init setting such as `SET time_zone = '+00:00'`, and test it. Do not assume defaults.
- Reporting dates are Indian local dates in `Asia/Kolkata` (IST). Convert local report start/end boundaries to UTC when querying timestamp data. Do not group rows by the UTC date when the user requested an IST date.
- Define and document the API timestamp format; ISO-8601 timestamps with explicit UTC `Z` are preferred for instants. Frontend display/report boundaries use `Asia/Kolkata` consistently.
- Add tests around midnight IST, month end, quarter/half-year/year boundaries, and Indian Financial Year boundary (1 April).

## 11. Immediate mandatory tasks before entities/services

### Task A — V2 bucket-constraint migration

1. Read the exact V1 migration file and inspect actual constraint names/definitions (`SHOW CREATE TABLE` / information schema as appropriate).
2. Do not alter V1. Create a new forward migration, suggested name `V2__tighten_money_entry_constraints.sql`.
3. Tighten checks so invalid combinations and `NULL` loopholes are rejected. MySQL `CHECK` constraints may yield UNKNOWN on NULL; tests must prove negative cases are rejected.
4. Test V2 first against `budget_db_migration_test`, which already has V1 applied. Do not drop or recreate this schema.
5. Verify Flyway history shows V1 and V2 successful. Run valid and invalid insert tests for all entry types and buckets; ensure test rows are cleaned up safely inside transactions or with a precise, reviewed cleanup step.
6. Only after successful test verification, apply V2 to `budget_db` after verifying the active `DB_URL` explicitly. Never use `flyway clean`.

### Task B — UTC/JDBC configuration

1. Inspect the current `application.properties` and runtime configuration.
2. Configure UTC for MySQL session/JDBC handling and Java instant conversions.
3. Keep `spring.jpa.hibernate.ddl-auto=none` while entities/migration are under development. Only consider `validate` after mapping compatibility is tested.
4. Add a test proving a timestamp near midnight IST appears in the correct local report date.

### Task C — then implement backend foundations

Use a simple layered layout:

- `entity`: only `User`, `MonthlyBudget`, `MoneyEntry` initially.
- `repository`: derived/query methods scoped by authenticated user ID.
- `dto`: request/response classes; never expose JPA entities directly.
- `service`: allocation engine, budget lifecycle, entry validation/accounting, credit-card outstanding, reports.
- `controller`: thin REST controllers; no business calculations.
- `config`: Spring Security, session/CSRF, timezone, CORS/dev settings.
- `exception`: domain exceptions and a global `@ControllerAdvice`.

Keep a single versioned API prefix `/api/v1` and align with the final requirements documents. Suggested essential endpoints (inspect local final requirements for full contract):

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/csrf` as needed by Spring Security configuration
- `GET /api/v1/me`, `PATCH /api/v1/me`
- `GET /api/v1/budgets/{yyyy-MM}`, `PUT /api/v1/budgets/{yyyy-MM}`
- `POST /api/v1/money-entries`
- `GET /api/v1/money-entries` with IST date filters and pagination
- `GET /api/v1/money-entries/{id}`
- `POST /api/v1/money-entries/{id}/void`
- `GET /api/v1/reference-data/categories`
- `GET /api/v1/reports/monthly-summary?month=YYYY-MM`
- `GET /api/v1/reports/summary?fromDate=&toDate=&groupBy=`
- `GET /api/v1/credit-card/summary`

The final API contract must come from the local final requirements files. If exact local documents are missing, report that and use the requirements in this prompt as the current contract; do not silently invent a divergent contract.

## 12. Security requirements — not optional

- Use Spring Security server-side session authentication with HttpOnly cookie for this Angular + Spring Boot app unless there is a documented reason to change it. Do not store sessions, passwords, or bearer/session tokens in localStorage.
- Use Spring Security `PasswordEncoder` / `DelegatingPasswordEncoder` with BCrypt or Argon2; never plaintext, `NoOpPasswordEncoder`, plain SHA-256, or reversible encryption for passwords.
- Keep CSRF enabled for state-changing cookie-authenticated requests; configure Angular's XSRF behavior correctly. Do not disable CSRF just to make a request succeed.
- Prefer an Angular development proxy for `/api` to `http://localhost:8080` to create same-origin local requests. If cross-origin CORS is necessary, permit only exact configured origins and never combine credentialed CORS with wildcard `*`.
- Resolve `user_id` from the authenticated principal/security context, not from request body/query parameter. Every user-owned query/update/void must include the authenticated user ID and target ID. Test that User A cannot read/edit/void User B data by guessing IDs.
- Normalize email; enforce unique email atomically; return a generic invalid-login message; don't reveal whether an account exists. Add login throttling/rate-limiting or a clearly tracked development-to-production task.
- Validate all request fields and cross-field business rules on the backend even if Angular validates them.
- Use consistent errors (`400`, `401`, `403`, `404`, `409`, `429`, generic `500`) with stable codes. Never return SQL/stack traces or secrets to clients.
- Runtime DB account should have only required runtime permissions; migration privileges should be separately considered for non-local deployments.
- Never print or inspect the actual values of `DB_PASSWORD`, API keys, session cookies, CSRF tokens, or password hashes. Do not commit credentials. `application.properties` must use environment variables; keep local config and `opencode.json` ignored.

## 13. Allocation and reporting status rules

- If the month has no budget: return/display `BUDGET_NOT_SET` distinctly; do not fabricate a zero budget.
- If monthly recorded income is zero: return/display `NO_INCOME`; show actual recorded activity without false “on track” reassurance.
- Otherwise, if any bucket exceeds its allocated amount: `OVER_ALLOCATION`; else `WITHIN_ALLOCATION`.
- Empty entry list is its own UI state, separate from financial status.
- V1 should not claim “Safe to Spend today,” forecast guaranteed savings, or offer investment advice. That formula is not approved for V1.
- Historical/past budgets read-only; do not count future budgets in lifetime-to-date Recorded Income.
- Dashboard/report totals should be computed from saved records on the server, not from downloading every entry and summing in Angular.

## 14. Angular UI integration rules

- Build real flows incrementally after the API contract is agreed: registration/login, current-month budget setup, add entry, entry history, month summary, date-filtered reports, profile/logout.
- Keep components presentational where possible and centralize all HTTP calls in Angular services. Avoid business calculations scattered through components.
- Use Angular standalone components and strict TypeScript.
- Use one API base path `/api/v1`. DTO types must match the backend contract exactly.
- Send/receive money amounts as decimal strings in JSON (for example `"30000.00"`) and format using a shared INR formatting utility. Do not use JS `number` calculations as the financial source of truth; display backend-returned allocation amounts.
- Use Angular HTTP/CSRF configuration compatible with Spring Security. Do not use localStorage for credentials/session tokens. Route guards are UX only; API authorization remains on server.
- Implement loading, empty, validation error, save failure, session-expired, forbidden, conflict, no-budget, no-income, over-allocation, success and confirmation states. Do not rely on color alone; keep labels accessible, keyboard usable, and responsive to small screens.
- On `409`, preserve form inputs and show the specific message instead of silently clearing them. On `401`, guide the user to log in again.
- Keep the first version welcoming and simple. Avoid showing database concepts, confusing finance jargon, or advanced future features in navigation.

## 15. Error contract and API behavior

Use a consistent problem response, e.g.:

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

Use this as a shape, not as a reason to return internal exception text. Important conflicts include duplicate monthly budgets, past-month edit, turning Investments OFF after a posted investment, oversize credit-card repayment, duplicate void and idempotency-key payload conflict.

## 16. Required automated tests

Backend unit tests:

- All preset percentages exactly match mode.
- Manual edit flips mode to `MANUAL`.
- Investments OFF forces 0% and Essentials + Safety = 100%.
- Investment-off toggle blocked if a posted investment exists; new investments rejected while off.
- BigDecimal allocation totals exactly match income for values that create paise remainders.
- Invalid entry type/bucket/category/payment-method combinations rejected.
- Void entry excluded from expense/investment/safety totals; duplicate void rejected.
- Repeating the same `client_request_id` cannot create duplicate entries.
- Credit-card purchase adds expenditure once and raises estimate; payment reduces estimate without increasing expense; overpayment rejected.
- Past months cannot be edited; future budget is excluded from lifetime-to-date recorded income.
- `BUDGET_NOT_SET`, `NO_INCOME`, `OVER_ALLOCATION`, and empty history behave distinctly.
- IST midnight/day/month/FY boundaries produce correct results.
- User A cannot access User B's budget or entries.

Migration/integration tests:

- Apply V1 + V2 to a clean/disposable schema and verify Flyway history.
- Apply V2 to the existing `budget_db_migration_test` schema where V1 is already applied.
- Prove both valid and invalid bucket/type combinations with inserts; no relying on constraint syntax alone.
- Prefer integration testing with the same MySQL family/version used by development; H2 alone is insufficient to prove MySQL check/DDL behavior.
- Run `.[mvnw.cmd clean test` (actual command: `.[mvnw.cmd clean test`) after relevant backend code changes.
- Run Angular tests/build, e.g. `npm.cmd test` if configured and `npm.cmd run build`; on this Windows PowerShell setup, `npm.ps1` was blocked earlier, so `.cmd` shims are safe until policy confirms otherwise.

End-to-end smoke journey:

Register/login → set current-month budget → add expense → confirm updated summary → filter history → logout.

## 17. Current task order (follow exactly)

**Start with AUDIT ONLY; do not modify files or execute SQL in the first response.**

1. Inspect both repositories' `git status`, the backend `pom.xml`, `application.properties`, V1 migration, all existing `src` classes/tests, Angular package/config/source files, and available docs.
2. Find and read these docs if present:
   - `md_files/DATABASE_IMPLEMENTATION_TODO.md`
   - `md_files/PROJECT_DETAILED_FEATURE_REQUIREMENTS.md`
   - `md_files/DATABASE_DESIGN.md` (older 33-table draft; explicitly obsolete for MVP schema)
   - `SERVICE_REQUIREMENTS_FINAL.md`
   - `UI_REQUIREMENTS_FINAL.md`
   - `MVP_TECHNICAL_REQUIREMENTS_GAP_ANALYSIS.md`, if present
3. The **current simplified MVP requirements in this prompt supersede the old 33-table draft**. Do not reintroduce the legacy 33-table schema. If final docs or TODO files are missing from local workspace, report their absence. Do not make up file contents. Use the instructions in this prompt as current baseline.
4. Report an audit summary: actual branch/worktree changes, schema/migration state, dependency/config status, and any discrepancies. Do not expose secret values.
5. Then propose precise changes for Task A V2 constraint migration and Task B UTC/timezone configuration. Inspect real V1 check-constraint names before writing V2. Do not edit V1. Do not apply migration to `budget_db` automatically. Test V2 against `budget_db_migration_test` first; report before applying it to `budget_db`.
6. Once these fixes are successful, proceed to JPA entities/repositories, then authentication/security, allocation service, entry service, reports, and finally Angular API integration—in small tested increments.

## 18. Hard safety rules

- Never drop, truncate, or recreate `budget_db`, `budget_db_migration_test`, or any table as a shortcut.
- Never run `flyway clean`.
- Never edit an already-applied V1 migration; write a forward V2/V3 migration.
- Before any Flyway run, print the non-secret JDBC URL and confirm the intended schema. If the URL is missing/ambiguous, stop.
- Do not assume environment variables are inherited by IntelliJ; verify presence without printing values.
- Do not commit/push unless explicitly asked. Before any commit, inspect staged diff for secrets and confirm the correct Git repository/branch.
- Do not download or add new dependencies casually. Check compatibility with Spring Boot `4.1.1` and its managed versions.
- Don't add a new business table without explaining the requirement and getting explicit product-owner approval.
- If any requirement conflicts or an operation could damage data, stop and ask one concise question instead of guessing.

## 19. Expected first response from you

Start by giving me:

1. A concise audit of the current workspace and the files you actually inspected.
2. A list of discrepancies or missing context, if any.
3. The exact proposed V2 migration rule and timezone configuration plan, without applying either yet.
4. The test commands you intend to run.
5. Explicit confirmation that you did not edit files, run SQL, or change a database during the audit.

Do not start by generating all controllers/entities/UI. First establish what is really present in the workspace and keep the project stable.
