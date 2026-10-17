# Budgeting App — Phase-Wise AI Coding Prompts

## Purpose

This document contains controlled prompts for Devin/Codex/FreeLLMAPI.

The goal is to prevent the coding agent from attempting the entire application at once, inventing requirements, changing business logic, or hallucinating architecture.

---

# GLOBAL AGENT RULES

Paste these rules at the beginning of every phase prompt.

```text
You are working on the Indian Family Money Planner project.

SOURCE OF TRUTH:
1. docs/PROJECT_DETAILED_FEATURE_REQUIREMENTS.md
2. docs/CODE_APP_ARCHITECTURE_FLOW.md
3. The current repository code
4. The current phase prompt

STRICT RULES:
- Work ONLY on the current phase.
- Do NOT implement future-phase features.
- Do NOT invent missing business rules.
- Do NOT silently change existing requirements.
- If requirements conflict, STOP and report the conflict before coding.
- Inspect the existing repository before modifying files.
- Reuse existing code where appropriate.
- Do not create duplicate services, models, utilities or business logic.
- Keep financial calculations deterministic.
- Never use AI/LLM output for authoritative financial arithmetic.
- Use BigDecimal for backend monetary calculations.
- Do not put secrets/API keys in source code.
- Do not connect the frontend directly to the database.
- Do not store bank credentials.
- Do not use the visible word "calculator" in product UI.
- Do not modify unrelated files.
- Add or update automated tests for business logic.
- Run the appropriate build/tests after implementation.
- If a test fails because of pre-existing code, identify it clearly.
- At the end, report:
  1. Files created
  2. Files modified
  3. Features implemented
  4. Tests executed
  5. Test results
  6. Remaining issues
  7. Any assumptions made

Before coding, provide a short implementation plan.
After the plan, execute it.
```

---

# PHASE 0 — Repository Audit & Project Setup

## Objective

Prepare the repository without implementing product features.

## Prompt

```text
Read:
- docs/PROJECT_DETAILED_FEATURE_REQUIREMENTS.md
- docs/CODE_APP_ARCHITECTURE_FLOW.md

Audit the current repository.

Do not build product features yet.

Identify:
- frontend technology/version
- backend technology/version
- database setup
- package/build tools
- existing modules
- existing environment files
- existing Docker files
- existing tests
- existing authentication
- existing API structure

Create a concise PROJECT_STATUS.md containing:
- Current state
- Existing components
- Missing components
- Risks
- Recommended next step

If the repository is empty, prepare the planned Angular + Spring Boot + MySQL structure.

Do not invent implementation details beyond the architecture document.
```

### Exit criteria

- Repository understood
- No feature implementation
- Build tools identified
- Baseline build succeeds or known failures documented

---

# PHASE 1 — Foundation & Database

## Objective

Create the technical foundation.

Implement only:

- Backend project structure
- Angular project structure
- Database connection
- Flyway/migration foundation
- Base configuration
- Docker development environment
- Health endpoint
- Basic error handling
- Basic API response conventions

Do NOT implement:
- Budget
- Expense tracking
- Goals
- AI
- Credit cards
- Yearly calculations

## Prompt

```text
Implement Phase 1 only.

Create the technical foundation described in the architecture document.

Requirements:
- Angular frontend
- Spring Boot backend
- MySQL
- Docker Compose
- Database migration foundation
- Environment-based configuration
- Health check endpoint
- Global backend exception handling
- Basic frontend API configuration

Do not implement financial features.

After implementation:
- Build frontend
- Build backend
- Start Docker services
- Verify backend can connect to database
- Verify health endpoint
- Report all changes
```

---

# PHASE 2 — Authentication & Household Foundation

## Objective

Build secure user/household foundations.

Implement:

- User
- Household
- Household members
- Authentication
- Authorization
- Password hashing
- API security
- Ownership checks

Do NOT implement financial transactions yet.

## Prompt

```text
Implement Phase 2 only.

Build authentication and household foundations according to the requirements and architecture documents.

Implement:
- Registration/login
- Password hashing
- Authentication
- Authorization
- User
- Household
- HouseholdMember
- Protected API routes
- Frontend login/session flow

Every protected endpoint must verify ownership server-side.

Add tests for:
- Valid login
- Invalid login
- Unauthorized API access
- User accessing another household's data

Do not implement budget or transaction features.
```

---

# PHASE 3 — Income & Yearly Salary Planning

## Objective

Build the yearly financial planning foundation.

Implement:

- Financial year
- Monthly income plan
- Salary
- Bonus
- Incentive
- One-time income
- Income classification
- Appraisal planning
- Annual income summary

## Prompt

```text
Implement Phase 3 only.

Build Yearly Salary Planning.

The system must support month-specific income.

Do NOT assume:
annual income = monthly salary × 12.

Support:
- Salary
- Bonus
- Incentive
- Arrears
- Other income
- Guaranteed/Expected/Variable/One-time classification
- Appraisal month
- Expected hike
- Monthly forecast

Use the Indian financial year April-March as the default.

Implement deterministic calculations and automated tests.

Do not implement expenses, investments, AI or daily tracking yet.
```

---

# PHASE 4 — Categories, Accounts & Transaction Engine

## Objective

Create the foundation for daily money tracking.

Implement:

- Accounts
- Categories
- Subcategories
- Transactions
- Income transactions
- Expense transactions
- Transfers
- Payment methods
- Indian categories

Important:

Transfers must not count as expenses.

## Prompt

```text
Implement Phase 4 only.

Build the transaction engine.

Implement:
- Account
- Category
- Subcategory
- Transaction
- Income
- Expense
- Transfer
- Payment methods

Include Indian categories from the requirements document.

Implement validation.

Critical accounting rule:
Transfers between a user's own accounts must not be counted as spending.

Add comprehensive transaction service tests.

Do not implement credit-card billing logic yet.
```

---

# PHASE 5 — Monthly Budget & Allocation Engine

## Objective

Implement the original monthly planning logic.

Implement:

- Conservative
- Balanced
- Aggressive
- Needs
- Emergency
- Investments
- Asset allocation
- Equity breakdown
- Debt breakdown
- Commodity breakdown

Fix the original double-allocation problem:

Asset allocation and goal allocation are separate dimensions.

## Prompt

```text
Implement Phase 5 only.

Implement the deterministic monthly allocation engine.

Use the exact allocation rules in:
docs/PROJECT_DETAILED_FEATURE_REQUIREMENTS.md

Requirements:
- Primary allocation
- Asset allocation
- Equity breakdown
- Debt breakdown
- Commodity breakdown
- Configurable rules
- 100% validation
- BigDecimal calculations
- Explicit rounding policy
- Unit tests for every allocation mode

Important:
Do NOT treat goal allocation as another subtraction from the investment amount.
Asset allocation and goal tagging are separate dimensions.

Do not implement AI.

Do not silently change the original percentages.
```

---

# PHASE 6 — Daily Budget, Safe-to-Spend & Recurring Expenses

## Objective

Make the product useful every day.

Implement:

- Fixed expenses
- Variable expenses
- Recurring transactions
- Safe-to-Spend
- Daily budget
- Upcoming commitments

## Prompt

```text
Implement Phase 6 only.

Build daily money management.

Implement:
- Fixed vs variable expenses
- Recurring expenses
- Upcoming committed expenses
- Safe-to-Spend calculation

Before coding Safe-to-Spend:
1. Find the current documented formula.
2. If the formula is not sufficiently defined, STOP and ask for clarification.
3. Do not invent a financial formula.

Handle:
- Month end
- Negative remaining budget
- Large upcoming bill
- Mid-month onboarding
- Irregular income

Add automated tests for all edge cases.
```

---

# PHASE 7 — Bills & Credit Cards

## Objective

Handle Indian bill/payment workflows correctly.

Implement:

- Bill calendar
- Due dates
- Credit cards
- Statement date
- Payment due date
- Outstanding
- Available limit
- Card transaction accounting

## Prompt

```text
Implement Phase 7 only.

Build Bills and Credit Card management.

Critical accounting rule:
A credit-card purchase is an expense.
Paying the credit-card bill is a settlement/transfer and must not create another expense.

Test:
- Card purchase
- Partial payment
- Full payment
- Due date
- Statement date
- Outstanding
- Available limit
- Double-count prevention

Do not implement investment portfolio tracking.
```

---

# PHASE 8 — Goals, Sinking Funds & Emergency Fund

## Objective

Build family financial goals.

Implement:

- Custom goals
- Goal contributions
- Goal progress
- Sinking funds
- Emergency fund target
- 3/6/9/12 month options
- Required contribution calculations

## Prompt

```text
Implement Phase 8 only.

Build:
- Goals
- Goal contributions
- Sinking funds
- Emergency fund

Support custom target amounts and dates.

Do not duplicate money.

A goal contribution must be traceable to the underlying account/transaction where appropriate.

Add tests for:
- Progress
- Target date
- Required contribution
- Completed goal
- Overfunded goal
- Emergency fund months coverage
```

---

# PHASE 9 — Yearly → Monthly → Daily Dashboard

## Objective

Connect planning and actual behavior.

Implement:

- Year dashboard
- Month dashboard
- Daily dashboard
- Drill-down
- Actual vs planned
- Annual expense calendar
- Monthly review

## Prompt

```text
Implement Phase 9 only.

Connect:
Yearly Plan → Monthly Budget → Daily Transactions.

Do not duplicate actual financial records.

Yearly and monthly dashboards must aggregate the underlying data.

Implement:
- Annual summary
- Month summary
- Daily summary
- Planned vs actual
- Annual expense calendar
- Monthly review

Every displayed number must come from deterministic backend data.
```

---

# PHASE 10 — Family Mode & Sharing

## Objective

Make the app practical for Indian couples/families.

Implement:

- Family member attribution
- Shared expenses
- Member contribution
- Partner-friendly summary
- WhatsApp share text

Do not expose all private data by default.

## Prompt

```text
Implement Phase 10 only.

Build family-aware budgeting.

Support:
- Household members
- Individual expense attribution
- Shared expenses
- Income contribution
- Partner summary

Implement WhatsApp-friendly sharing.

Sharing must be explicit and user-controlled.

Do not send private transaction details unless the user selects them.
```

---

# PHASE 11 — Net Worth & Indian Financial Accounts

## Objective

Add financial position tracking.

Implement:

- Assets
- Liabilities
- Net worth
- EPF
- PPF
- NPS
- FD
- RD
- Gold
- Silver
- Investment holdings foundation

## Prompt

```text
Implement Phase 11 only.

Build net-worth tracking and Indian financial account types.

Formula:
Net Worth = Assets - Liabilities

Do not connect to banks.

Do not invent live investment prices.

Manual values are acceptable for this phase.

Add tests for net-worth aggregation.
```

---

# PHASE 12 — AI Money Assistant

## Objective

Add AI only after deterministic financial systems are stable.

## Prompt

```text
Implement Phase 12 only.

Integrate the AI layer through the Spring Boot backend.

Architecture:
Angular → Spring Boot AI API → AI Service → FreeLLMAPI

Never expose the FreeLLMAPI key to the browser.

AI may:
- Explain
- Summarize
- Answer questions from supplied structured financial data
- Suggest budgeting improvements

AI may NOT:
- Perform authoritative financial calculations
- Invent balances
- Invent transactions
- Invent goals
- Invent bills
- Pretend to know data not supplied

For every financial calculation, use deterministic backend services.

If the AI response conflicts with backend numbers, backend numbers are authoritative.
```

---

# PHASE 13 — Security, Privacy & Production Hardening

## Objective

Prepare for real users.

Implement/review:

- HTTPS
- CORS
- Security headers
- Rate limiting where appropriate
- Input validation
- Authorization
- Secret management
- Audit logging
- Error handling
- Data export
- Data deletion
- Backup strategy
- Privacy policy support

## Prompt

```text
Implement Phase 13 only.

Perform a security and privacy hardening pass.

Do not redesign business functionality.

Audit:
- Authentication
- Authorization
- Secrets
- API exposure
- Database access
- CORS
- Input validation
- Logging
- Error messages
- Sensitive data handling
- AI data transmission

Identify vulnerabilities before fixing them.

Report:
- Critical
- High
- Medium
- Low

Fix critical/high issues in scope.
```

---

# PHASE 14 — Testing, Performance & Release Candidate

## Objective

Prepare production release.

Implement:

- Unit tests
- Integration tests
- E2E tests
- Regression tests
- Performance checks
- Mobile responsive QA
- Accessibility review
- Production builds
- Docker images

## Prompt

```text
Implement Phase 14 only.

Do not add new product features.

Perform release-candidate validation.

Run:
- Backend tests
- Frontend tests
- API tests
- E2E tests
- Production builds
- Docker build
- Basic performance checks
- Responsive checks

Find and fix release-blocking defects.

Produce a RELEASE_CHECKLIST.md.
```

---

# PHASE 15 — Android APK / AAB

## Objective

Package the web application as Android.

Technology:

- Capacitor
- Android Studio
- Android SDK

Flow:

```text
Angular production build
→ Capacitor sync
→ Android project
→ Android Studio
→ Debug APK
→ Release AAB/APK
```

## Prompt

```text
Implement Phase 15 only.

Add Capacitor Android support to the existing Angular application.

Do not rewrite the Angular app as a native Android app.

Configure:
- App ID
- App name
- App icon
- Splash screen
- Android permissions only when required
- Production API URL

Build and test:
- Android emulator
- Physical Android device if available

Generate a debug APK first.

Do not create a release signing key without explicit instruction.
```

---

# PHASE 16 — Production Deployment

## Objective

Deploy the complete system online.

Recommended architecture:

```text
Domain
  ↓
HTTPS
  ↓
Angular frontend
  ↓
Spring Boot API
  ↓
MySQL
```

Dockerize:

- Frontend
- Backend
- Database where appropriate

## Prompt

```text
Implement Phase 16 only.

Prepare production deployment.

Create:
- Production Dockerfiles
- Docker Compose or deployment configuration
- Production environment configuration
- Database migration process
- Health checks
- Logging configuration

Do not put secrets into Git.

Do not deploy until:
- Production build succeeds
- Database migrations are verified
- Authentication is tested
- API authorization is tested
- Backup strategy exists
```

---

# PHASE 17 — Post-Launch Features

Only after the core product is stable:

- Offline-first sync
- CSV import
- Advanced reports
- Tax planner
- Bank/account integrations
- Automatic categorization
- Advanced notifications
- AI insights
- Advanced family collaboration

These should each receive their own mini-requirement before coding.

---

# PHASE COMPLETION TEMPLATE

At the end of every phase, the coding agent must return:

```text
PHASE:
STATUS:

IMPLEMENTED:
- ...

FILES CREATED:
- ...

FILES MODIFIED:
- ...

DATABASE CHANGES:
- ...

API CHANGES:
- ...

UI CHANGES:
- ...

TESTS:
- ...

BUILD:
- ...

KNOWN ISSUES:
- ...

ASSUMPTIONS:
- ...

OUT OF SCOPE:
- ...
```

---

# NEVER DO THIS

Do not give the coding agent a prompt such as:

"Build the entire budgeting app."

That invites:
- Hallucinated requirements
- Huge uncontrolled changes
- Duplicate code
- Incorrect financial formulas
- Broken architecture
- Incomplete testing

Always execute one phase at a time.
