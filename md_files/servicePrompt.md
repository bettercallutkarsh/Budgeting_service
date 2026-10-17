# servicePrompt.md

# Family Budgeting App — Backend / Service-Side Phase Prompts

## Purpose
This is the authoritative execution prompt for the Java/Spring Boot/MySQL backend.

Before every phase, inspect:
1. `PROJECT_DETAILED_FEATURE_REQUIREMENTS.md`
2. `CODE_APP_ARCHITECTURE_FLOW.md`
3. `PROJECT_DECISIONS.md` if present
4. this file
5. the current repository state

If requirements conflict, STOP and report the conflict. Do not invent financial or product rules.

## Backend Boundary
Allowed:
- Java/Spring Boot
- Maven
- JPA/Hibernate
- Flyway migrations
- MySQL-related backend configuration
- REST controllers/services/repositories
- backend tests
- backend API documentation

Do not modify Angular during backend-only phases.

## Non-Negotiable Rules
- Backend is the source of truth for financial calculations.
- Use `BigDecimal` for money; never use `double`/`float` for authoritative financial values.
- Define one consistent scale/rounding/reconciliation policy.
- Use Flyway for schema evolution.
- Do not rely on `ddl-auto=create` or `create-drop` for persistent environments.
- Angular communicates only through REST APIs.
- Never expose DB credentials or provider secrets.
- Never put FreeLLMAPI/API keys in frontend code.
- AI may explain structured financial data but must not become the calculation authority.
- Docker is optional during local development; do not make early phases depend on Docker.

## Core Allocation Rules

### Primary allocation
Conservative: Needs 60%, Emergency 10%, Investments 30%
Balanced: Needs 55%, Emergency 10%, Investments 35%
Aggressive: Needs 50%, Emergency 10%, Investments 40%

### Investment asset allocation
Conservative: Equity 40%, Debt 50%, Commodities 8%, Crypto 2%
Balanced: Equity 60%, Debt 30%, Commodities 8%, Crypto 2%
Aggressive: Equity 70%, Debt 20%, Commodities 8%, Crypto 2%

Crypto must be treated as optional/high-risk in the product direction. Do not silently force crypto into a user's real plan unless the product requirement explicitly enables it.

### Equity allocation
Conservative: Large 70%, Mid 25%, Small 5%
Balanced: Large 60%, Mid 30%, Small 10%
Aggressive: Large 50%, Mid 35%, Small 15%

### Debt allocation
Conservative: Liquid/Overnight 40%, Short Duration/TMF 40%, Target Maturity 20%
Balanced: Liquid/Overnight 30%, Short Duration/TMF 45%, Target Maturity 25%
Aggressive: Liquid/Overnight 20%, Short Duration/TMF 40%, Target Maturity 40%

### Commodity allocation
Conservative: Gold 85%, Silver 15%
Balanced: Gold 80%, Silver 20%
Aggressive: Gold 75%, Silver 25%

### Goal allocation
Conservative: Education 45%, Retirement 35%, Home 15%, Vacation 5%
Balanced: Education 40%, Retirement 40%, Home 15%, Vacation 5%
Aggressive: Education 35%, Retirement 45%, Home 15%, Vacation 5%

IMPORTANT:
Asset allocation and goal allocation are separate dimensions.
Example: an Equity investment can be tagged to the Retirement goal.
Do not double-allocate the same money merely because both dimensions total 100%.

## API Synchronization Contract
For every API:
1. Define request DTO.
2. Define response DTO.
3. Define validation rules.
4. Define error response.
5. Add backend tests.
6. Document the endpoint.
7. Tell the frontend phase exactly what changed.

Recommended error shape:
```json
{
  "timestamp": "...",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Human readable message",
  "fieldErrors": {
    "income": "Income must be greater than zero"
  },
  "path": "/api/v1/..."
}
```

---

# PHASE 0 — Repository Audit

Inspect the backend without implementing new features.

Check:
- Java/Spring Boot versions
- Maven
- existing packages
- controllers/services/repositories/entities
- DB configuration
- migrations
- tests
- API endpoints
- secrets/configuration
- duplicate/abandoned code
- relationship with Angular repository

Deliver an audit report with current structure, APIs, DB state, risks, missing pieces and cleanup recommendations.

Exit: backend is understood and no critical architecture conflict remains.

---

# PHASE 1 — Backend Foundation

Build:
- feature-based package structure
- profiles/configuration
- MySQL connection
- Flyway
- JPA/Hibernate
- validation
- global exception handling
- API error conventions
- health endpoint
- logging conventions
- base tests
- environment-based secret configuration

Suggested package structure:
```text
com.example.budgeting
├── config
├── common
│   ├── exception
│   ├── response
│   └── validation
├── auth
├── household
├── income
├── budget
├── transaction
├── account
├── bill
├── creditcard
├── goal
├── dashboard
├── allocation
└── ai
```

Exit:
- backend starts locally
- MySQL connects
- Flyway works
- health endpoint works
- tests pass

---

# PHASE 2 — Authentication + Household

Implement:
- User
- Household/family
- Household membership
- roles/permissions
- authentication
- authorization

Suggested roles:
- OWNER
- MEMBER

Suggested API contracts:
```text
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/users/me
POST /api/v1/households
GET  /api/v1/households/current
POST /api/v1/households/members
```

Protect household data from unauthorized access.

Exit: authentication, household ownership and authorization are tested.

---

# PHASE 3 — Yearly Salary / Income Planning

Default financial year:
`April → March`

Do NOT calculate annual income as monthly salary × 12.

Support income sources:
- Salary
- Bonus
- Performance bonus
- Incentive
- Arrears
- Freelance
- Business
- Rental
- Interest
- Other

Classifications:
- Guaranteed
- Expected
- Variable
- One-time

Appraisal planner:
- current take-home
- appraisal month
- expected hike %
- projected salary

Implement APIs for:
- financial year
- income CRUD
- annual income summary
- monthly income timeline
- classification summary
- appraisal projection

Test:
- April–March boundaries
- variable/one-time income
- appraisal month
- invalid amounts
- annual aggregation

Exit: frontend can obtain a complete annual income timeline and summary.

---

# PHASE 4 — Accounts, Categories, Transactions

Implement account types for:
- Cash
- Bank
- UPI-linked account representation
- Credit card
- Investment account
- Other

Indian-first categories should cover household, transport, family and financial categories from the requirements.

Transaction fields should support:
- household
- account
- category
- amount
- date/time
- type
- notes
- optional goal
- tags

CRITICAL CREDIT CARD RULE:
`credit-card purchase = expense`
`credit-card bill payment = settlement/transfer`

A bill settlement must not create a second expense.

Exit:
- CRUD works
- categories/accounts work
- credit-card double counting is prevented
- tests pass

---

# PHASE 5 — Monthly Budget + Allocation Engine

Create one authoritative allocation service.

Suggested services:
- `AllocationService`
- `BudgetCalculationService`

Input:
- monthly take-home income
- allocation mode

Output:
- needs
- emergency
- investments
- asset allocation
- equity breakdown
- debt breakdown
- commodity breakdown
- goal allocation

Use `BigDecimal`.

Every layer must reconcile:
```text
needs + emergency + investments = income
equity + debt + commodities + crypto = investments
large + mid + small = equity
liquid + short-duration + target-maturity = debt
gold + silver = commodities
goals = investment amount
```

Define one rounding/reconciliation policy. If rounded child values leave a residual, reconcile it deterministically.

Reference:
₹1,00,000 Balanced:
- Needs ₹55,000
- Emergency ₹10,000
- Investments ₹35,000

Exit: all financial allocation tests pass and frontend can consume a complete plan response.

---

# PHASE 6 — Safe-to-Spend + Recurring Expenses

Implement:
- fixed expenses
- variable expenses
- recurring expenses
- daily spending
- remaining monthly budget
- committed recurring obligations
- Safe-to-Spend Today

IMPORTANT:
The exact Safe-to-Spend formula must be defined in `PROJECT_DECISIONS.md` before implementation if it is not already defined.

Backend owns the calculation.

Exit: API returns consistent daily spending data with clear time-zone/date handling.

---

# PHASE 7 — Bills + Credit Cards

Bills:
- due date
- frequency
- amount
- category
- account
- reminder state
- paid/unpaid

Credit cards:
- card name
- limit
- statement date
- due date
- outstanding
- available limit
- transactions

Preserve the purchase-vs-settlement rule.

Exit: credit-card balances and expenses never double count.

---

# PHASE 8 — Goals + Sinking Funds + Emergency Fund

Goals:
- Child Education
- Retirement
- Home Purchase
- Vacation
- Custom

Goal fields:
- target amount
- target date
- current amount
- monthly contribution
- priority
- status

Sinking funds:
- Insurance
- School fees
- Diwali
- Vehicle service
- Property tax
- Vacation
- Annual subscriptions

Emergency fund:
- 3 months
- 6 months
- 9 months
- 12 months

Do not assume that 10% of income is permanently equivalent to the user's emergency-fund target.

Exit: goals/sinking funds/emergency planning are independent of asset allocation.

---

# PHASE 9 — Yearly → Monthly → Daily Dashboard APIs

Expose coherent:
`Year → Month → Day`

Provide APIs for:
- yearly summary
- monthly summary
- daily summary
- income
- spending
- budget health
- goals
- bills
- safe-to-spend
- investment plan

The frontend consumes these results rather than recreating totals.

Exit: yearly/monthly/daily numbers are consistent.

---

# PHASE 10 — Family Mode + Sharing

Implement backend support for:
- household members
- permissions
- shared financial data
- ownership rules
- appropriate audit information

Do not implement UI in this phase.

Exit: authorized household members can access permitted shared data only.

---

# PHASE 11 — Net Worth + Indian Financial Accounts

Support domain representation for:
Assets:
- Bank
- Cash
- EPF
- PPF
- NPS
- RD/FD
- Investments
- Gold
- Property

Liabilities:
- Loans
- Credit cards
- Other

Formula:
`Net Worth = Total Assets - Total Liabilities`

Do not claim live market values without verified integration.

---

# PHASE 12 — AI Money Assistant

Architecture:
`Angular → Spring Boot AI endpoint → structured financial context → FreeLLMAPI`

AI can:
- explain spending
- summarize budget health
- explain Safe-to-Spend
- suggest areas to review
- explain an existing plan

AI must not:
- calculate authoritative financial numbers independently
- execute financial transactions
- receive bank credentials
- receive API keys/secrets

Send minimum necessary structured context.

---

# PHASE 13 — Security / Privacy Hardening

Review:
- authorization
- validation
- secret handling
- CORS
- rate limiting where appropriate
- audit logging where needed
- sensitive-data minimization
- secure error responses
- dependency/security review

Never expose stack traces or internal exception details in production.

---

# PHASE 14 — Testing / Release Candidate

Test:
- unit
- service
- repository
- controller/API
- integration
- financial reconciliation
- authorization
- validation
- relevant concurrency behavior

Mandatory financial assertions:
- primary allocations reconcile
- asset allocations reconcile
- equity reconciles
- debt reconciles
- commodities reconcile
- goals reconcile

Test all three modes and multiple income values.

---

# PHASE 15 — Android Support

Keep backend platform-independent.

Same APIs must support:
- Angular web
- Capacitor Android

Do not create Android-specific financial logic.

---

# PHASE 16 — Production Deployment

Only after release candidate approval.

Possible architecture:
`HTTPS/Reverse Proxy → Angular → Spring Boot → secured MySQL`

Docker may be introduced here if selected for deployment. Do not make Docker an invisible dependency of earlier development phases.

---

# BACKEND ↔ FRONTEND SYNCHRONIZATION

For every phase:
1. Backend finishes its contract first.
2. Backend publishes request/response DTOs and endpoint behavior.
3. Backend adds tests.
4. Frontend consumes that exact contract.
5. Integration verification follows.
6. Do not let frontend invent missing backend behavior.
7. Keep enum values, field names, validation rules and error codes synchronized.

Recommended sequence:
`Backend phase → Backend tests/contract → Frontend phase → Integration verification`

---

# PHASE COMPLETION REPORT

Every phase must report:
`PHASE:`
`STATUS:`
`Implemented:`
`Files changed:`
`Database migrations:`
`APIs added/changed:`
`Tests added/executed:`
`Build result:`
`Frontend impact:`
`Required frontend work:`
`Known issues:`
`Assumptions:`
`Next phase:`

Never silently continue into the next phase.

# STOP CONDITIONS

STOP and report if:
- requirements conflict
- a financial rule is missing
- API contract is ambiguous
- database ownership is unclear
- frontend change is required during backend-only work
- calculation cannot reconcile
- security decision is unclear

# GOLDEN RULE

The backend calculates.
The frontend presents.
The database persists.
The AI assists.
The user remains in control.
