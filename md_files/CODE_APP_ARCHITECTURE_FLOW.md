# Budgeting App — Code Architecture & Application Flow

**Working stack:** Angular + Spring Boot + MySQL + REST API + Docker  
**Mobile target:** Android through Capacitor  
**AI layer:** FreeLLMAPI-compatible OpenAI API abstraction  
**Architecture style:** Modular monolith first; avoid premature microservices.

---

# 1. High-Level Architecture

```text
                    ┌─────────────────────────┐
                    │       User Device       │
                    │                         │
                    │  Web Browser / Android  │
                    └────────────┬────────────┘
                                 │
                     HTTPS / REST / JSON
                                 │
                    ┌────────────▼────────────┐
                    │      Angular Frontend   │
                    │                         │
                    │ UI / State / Services   │
                    │ Validation / Charts     │
                    └────────────┬────────────┘
                                 │
                           REST API
                                 │
                    ┌────────────▼────────────┐
                    │      Spring Boot API    │
                    │                         │
                    │ Controllers             │
                    │ Services                │
                    │ Business Rules          │
                    │ Security                │
                    │ Validation              │
                    └───────┬────────┬────────┘
                            │        │
                    ┌───────▼───┐ ┌─▼──────────────┐
                    │  MySQL    │ │ AI Provider     │
                    │ Database  │ │ FreeLLMAPI      │
                    └───────────┘ └────────────────┘
```

---

# 2. Architectural Principle

The frontend must NEVER be the financial source of truth.

### Frontend responsibilities

- Display
- Input collection
- Form validation for user experience
- Navigation
- Charts
- Local UI state
- Calling APIs

### Backend responsibilities

- Authentication
- Authorization
- Business rules
- Budget calculations
- Safe-to-Spend calculations
- Recurring transaction generation
- Goal calculations
- Financial-year calculations
- Data validation
- Persistence
- Audit-sensitive operations

### AI responsibilities

- Explanation
- Summarization
- Natural-language interface
- Suggestions based on deterministic application data

AI must not be responsible for core financial arithmetic.

---

# 3. Suggested Repository Structure

```text
budgeting-app/
│
├── docs/
│   ├── PROJECT_DETAILED_FEATURE_REQUIREMENTS.md
│   ├── CODE_APP_ARCHITECTURE_FLOW.md
│   └── PHASE_WISE_PROMPTS.md
│
├── frontend/
│   └── angular-app/
│
├── backend/
│   └── spring-boot-api/
│
├── mobile/
│   └── android/              # generated/managed by Capacitor
│
├── database/
│   ├── migrations/
│   └── seed/
│
├── docker/
│   ├── frontend/
│   ├── backend/
│   └── mysql/
│
├── docker-compose.yml
├── .gitignore
├── README.md
└── LICENSE
```

---

# 4. Backend Package Structure

Suggested modular structure:

```text
com.example.budgeting
│
├── config/
├── security/
├── common/
│   ├── exception/
│   ├── response/
│   └── validation/
│
├── auth/
├── user/
├── household/
├── member/
├── account/
├── transaction/
├── category/
├── budget/
├── recurring/
├── bill/
├── creditcard/
├── goal/
├── sinkingfund/
├── income/
├── financialyear/
├── investment/
├── networth/
├── notification/
├── report/
└── ai/
```

Use feature-based modules rather than one giant `controller/service/repository` package.

---

# 5. Core Database Entities

Initial model:

```text
User
Household
HouseholdMember
Account
Category
Transaction
Income
Budget
RecurringTransaction
Bill
CreditCard
Goal
GoalContribution
SinkingFund
FinancialYearPlan
MonthlyPlan
Notification
```

Later:

```text
InvestmentHolding
Asset
Liability
NetWorthSnapshot
AIInsight
TaxPlan
ImportJob
```

---

# 6. Entity Relationships

```text
User
  │
  └── Household
        │
        ├── HouseholdMember
        │
        ├── Account
        │      └── Transaction
        │
        ├── CreditCard
        │      └── CreditCardTransaction
        │
        ├── Income
        │
        ├── Budget
        │
        ├── RecurringTransaction
        │
        ├── Bill
        │
        ├── Goal
        │      └── GoalContribution
        │
        └── SinkingFund
```

A user may eventually belong to multiple households, but V1 can keep the model simpler if necessary.

---

# 7. Transaction Design

Transaction fields should include conceptually:

```text
id
householdId
memberId
accountId
categoryId
amount
transactionType
paymentMethod
transactionDate
description
isRecurringGenerated
createdAt
updatedAt
```

Transaction type:

- EXPENSE
- INCOME
- TRANSFER

Important:

A transfer between two owned accounts must NOT count as spending.

---

# 8. Credit Card Accounting Rule

Example:

User buys groceries for ₹2,000 using credit card.

At purchase:

```text
Expense = ₹2,000
Account = Credit Card
```

When paying the credit-card bill:

```text
Transfer / Settlement = ₹2,000
```

The bill payment must not create another ₹2,000 expense.

This prevents double counting.

---

# 9. Financial Year Model

Default Indian financial year:

```text
FY 2026-27
Start: 2026-04-01
End:   2027-03-31
```

The system should derive financial year from dates rather than hardcoding a single year.

A FinancialYearPlan can contain:

- Expected income
- Expected expenses
- Planned investments
- Planned goals
- Sinking funds
- Emergency fund target

---

# 10. Yearly → Monthly Relationship

```text
FinancialYearPlan
       │
       ├── MonthlyPlan (April)
       ├── MonthlyPlan (May)
       ├── MonthlyPlan (June)
       ├── ...
       └── MonthlyPlan (March)
```

Monthly actuals should come from transactions/income records.

Do not duplicate the same actual transaction as both yearly and monthly transaction data.

Yearly views should aggregate the underlying records.

---

# 11. Budget Flow

```text
Income
  ↓
Committed Expenses
  ↓
Variable Budget
  ↓
Savings / Emergency
  ↓
Investments
  ↓
Goals
  ↓
Flexible Money
```

Actual spending:

```text
Transaction
  ↓
Category
  ↓
Budget Bucket
  ↓
Actual vs Planned
  ↓
Remaining Budget
```

---

# 12. Safe-to-Spend Flow

Conceptual flow:

```text
Current Period Income
        ↓
- Committed Upcoming Payments
        ↓
- Planned Savings / Investments
        ↓
- Already Spent
        ↓
Remaining Flexible Budget
        ↓
Remaining Days
        ↓
Safe-to-Spend Guidance
```

The exact formula must be finalized in the requirements/business-rules document before implementation.

Edge cases:

- Last day of month
- No remaining budget
- Negative remaining budget
- Large upcoming bill
- Irregular income
- User changes budget mid-month
- User starts tracking mid-month

---

# 13. Allocation Engine

Create one deterministic backend service.

Example conceptual interface:

```text
AllocationService
    calculatePrimaryAllocation()
    calculateAssetAllocation()
    calculateEquityAllocation()
    calculateDebtAllocation()
    calculateCommodityAllocation()
    validateAllocationTotals()
```

Never duplicate formulas across multiple controllers/components.

All percentages should come from configuration/domain rules.

---

# 14. Allocation Rule Validation

Every allocation set must total exactly 100%.

Example:

```text
Needs + Emergency + Investments = 100%
```

Asset allocation:

```text
Equity + Debt + Commodities + Crypto = 100%
```

Sub-allocations:

```text
Large Cap + Mid Cap + Small Cap = 100%
```

etc.

The application must reject invalid configuration rather than silently producing incorrect results.

---

# 15. Money Precision

Do NOT use JavaScript floating-point arithmetic for authoritative financial calculations.

Backend:

- Use `BigDecimal` for monetary values.
- Define currency precision rules.
- Define rounding strategy.
- Store currency explicitly where future multi-currency support may be needed.

Frontend should display rounded Indian Rupee values but backend remains authoritative.

---

# 16. API Structure

Example:

```text
/api/v1/auth
/api/v1/users
/api/v1/households
/api/v1/members

/api/v1/income
/api/v1/accounts
/api/v1/transactions
/api/v1/categories

/api/v1/budgets
/api/v1/recurring
/api/v1/bills
/api/v1/credit-cards

/api/v1/goals
/api/v1/sinking-funds

/api/v1/financial-years
/api/v1/monthly-plans

/api/v1/investments
/api/v1/net-worth

/api/v1/reports
/api/v1/ai
```

Use versioned APIs from the beginning.

---

# 17. Angular Structure

Suggested:

```text
src/app/
│
├── core/
│   ├── auth/
│   ├── guards/
│   ├── interceptors/
│   ├── services/
│   └── models/
│
├── shared/
│   ├── components/
│   ├── pipes/
│   ├── directives/
│   └── utils/
│
├── features/
│   ├── dashboard/
│   ├── transactions/
│   ├── budget/
│   ├── yearly-plan/
│   ├── goals/
│   ├── bills/
│   ├── recurring/
│   ├── credit-cards/
│   ├── family/
│   ├── reports/
│   └── settings/
│
└── app.routes.ts
```

Prefer standalone Angular architecture.

---

# 18. State Management

Do not introduce a large state-management library immediately.

Start with:

- Angular Signals
- Feature services
- HTTP services
- Local component state

Introduce a dedicated global state library only if actual application complexity requires it.

---

# 19. Authentication Flow

```text
Login
  ↓
Backend authenticates
  ↓
Access token/session
  ↓
Angular stores session securely
  ↓
HTTP interceptor attaches authorization
  ↓
Backend verifies user
  ↓
Backend resolves household
  ↓
Data returned only for authorized household
```

Never trust `householdId` supplied by the browser without server-side authorization.

---

# 20. AI Architecture

Do not call FreeLLMAPI directly from Angular.

Use:

```text
Angular
  ↓
Spring Boot /api/v1/ai
  ↓
AI Service
  ↓
FreeLLMAPI
  ↓
Configured model/provider
```

This prevents API keys from being exposed in the browser.

AI prompt input should preferably be structured:

```text
{
  income: ...,
  currentBudget: ...,
  spendingSummary: ...,
  upcomingBills: ...,
  goalProgress: ...
}
```

rather than dumping the entire database.

---

# 21. Offline Strategy

V1:

- Web-first
- Online backend required
- Basic temporary local UI state only

Later:

- Capacitor
- Local SQLite
- Offline transaction queue
- Sync engine
- Conflict resolution

Do not build offline sync in the first phase unless it becomes a strict product requirement.

---

# 22. Android Architecture

Use Angular as the web application and Capacitor as the native Android shell.

```text
Angular build
    ↓
Capacitor sync
    ↓
Android project
    ↓
Android Studio
    ↓
APK / AAB
```

Capacitor officially supports Android, iOS and Web from a web-first application.

---

# 23. Deployment Architecture

Recommended simple production path:

```text
                 INTERNET
                    │
             HTTPS / DOMAIN
                    │
        ┌───────────┴───────────┐
        │                       │
   Angular Frontend       Spring Boot API
        │                       │
   Static Hosting          Docker Container
                                │
                              MySQL
```

Alternative:

```text
Single VPS
   ├── Nginx
   ├── Angular container
   ├── Spring Boot container
   └── MySQL container
```

For an early production release, a Dockerized VPS is simple to understand and gives you control.

---

# 24. Docker Development

Use:

```text
frontend container
backend container
mysql container
```

and:

```text
docker-compose.yml
```

Keep environment variables outside committed source.

Example:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD

JWT_SECRET

AI_BASE_URL
AI_API_KEY
AI_MODEL
```

Never commit actual secrets.

---

# 25. Environment Separation

Create:

```text
application-dev
application-test
application-staging
application-prod
```

Angular:

```text
development
staging
production
```

Backend and frontend must never accidentally point production users to development databases.

---

# 26. Testing Architecture

Backend:

- Unit tests
- Service/business-rule tests
- Repository/integration tests
- API tests

Frontend:

- Component tests
- Service tests
- Form validation tests

End-to-end:

- Login
- Create budget
- Add expense
- Add bill
- Check Safe-to-Spend
- Create goal
- Yearly plan
- Monthly review

Critical financial formulas require automated tests with known expected values.

---

# 27. Logging & Error Handling

Backend must provide consistent error responses.

Example conceptual structure:

```text
timestamp
status
code
message
path
validationErrors
```

Do not return stack traces to users.

Log technical details server-side.

Do not log:

- Passwords
- Authentication tokens
- API keys
- Sensitive financial payloads unnecessarily

---

# 28. Database Migration

Do not rely on automatic schema creation in production.

Use database migration tooling such as:

- Flyway

Development may use controlled schema generation initially, but production must use versioned migrations.

---

# 29. Key Engineering Rules

1. No duplicated business formulas.
2. No money calculations in templates.
3. No AI-generated financial arithmetic.
4. No frontend database access.
5. No raw database credentials in frontend.
6. No secrets in Git.
7. No direct production DB manipulation.
8. No breaking existing features without explicit phase scope.
9. Every major business rule gets automated tests.
10. Every phase ends with a build + test + regression check.

---

# 30. Final Application Flow

```text
USER
 │
 ├── Choose Yearly / Monthly / Smart
 │
 ├── Enter income
 │
 ├── Configure family
 │
 ├── Create plan
 │
 ▼
YEARLY PLAN
 │
 ├── Expected salary
 ├── Bonus
 ├── Annual expenses
 ├── Goals
 └── Sinking funds
 │
 ▼
MONTHLY PLAN
 │
 ├── Budget
 ├── Bills
 ├── Investments
 └── Goal contribution
 │
 ▼
DAILY MONEY
 │
 ├── Add expense
 ├── Track spending
 ├── Safe-to-Spend
 └── Alerts
 │
 ▼
REVIEW
 │
 ├── Monthly review
 ├── Yearly progress
 ├── Goal progress
 └── Financial health
 │
 ▼
OPTIONAL AI
 │
 └── Explain / summarize / suggest
```
