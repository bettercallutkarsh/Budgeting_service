# UIPrompt.md

# Family Budgeting App — Frontend / UI Phase Prompts

## Purpose
This is the authoritative execution prompt for the Angular frontend.

Before every phase, inspect:
1. `PROJECT_DETAILED_FEATURE_REQUIREMENTS.md`
2. `CODE_APP_ARCHITECTURE_FLOW.md`
3. `PROJECT_DECISIONS.md` if present
4. the current backend API contract
5. the current repository state

If requirements conflict, STOP and report the conflict. Do not invent product or financial rules.

## Frontend Boundary
Allowed: Angular/TypeScript, templates, styles, routes, frontend services, state, assets, frontend tests.

Not allowed during UI-only work: Spring Boot, MySQL schema, Flyway migrations, backend business logic.

If a backend change is required, STOP and report it.

## Non-Negotiable Rules
- Backend is authoritative for financial calculations.
- Angular presents backend results; it does not independently own financial formulas.
- Never call MySQL directly from Angular.
- Never expose FreeLLMAPI/provider/API keys in the browser.
- Use typed API models/services.
- Every API screen handles loading, success, empty, validation-error and server-error states.
- Never use the word `calculator` in visible UI.
- Do not invent API fields, enum values or business rules.
- Use mobile-first responsive design.
- Visual direction: warm cream/off-white, navy/charcoal, gold accents, green healthy states, amber caution, red only for high-risk/caution, rounded cards, soft shadows, readable numbers, progress bars/donut charts where useful.
- Tone: warm, practical, premium and family-friendly.
- Do not imply guaranteed, assured or risk-free investment returns.

## API Architecture
Use:
`Component → Feature Service → HTTP Client → Spring Boot API`

Do not put authoritative business calculations in components.

---

# PHASE 0 — Repository Audit

Inspect the Angular repository without implementing product features.

Check:
- Angular/TypeScript versions
- standalone architecture
- routing
- existing components/pages
- services
- HTTP configuration
- environment files
- state management
- tests
- styling
- duplicate/abandoned code

Deliver a report of structure, routes, services, risks, missing foundation and cleanup recommendations.

Exit: repository is understood.

---

# PHASE 1 — Frontend Foundation

Build:
- application shell
- routing foundation
- responsive layout
- navigation
- reusable cards/layout primitives
- API environment configuration
- HTTP client
- loading/error/empty-state patterns
- reusable currency formatting
- reusable form patterns

Do NOT build the full finance product.

Exit: Angular starts cleanly and the shell works on desktop/mobile.

---

# PHASE 2 — Authentication + Household UI

Build:
- registration
- login/logout
- session state
- current user
- household creation/view
- household members
- role-aware UI
- protected routes

Use backend contracts exactly. Do not invent authentication behavior.

Exit: Register → Login → Household works.

---

# PHASE 3 — Yearly Salary / Income Planning UI

Default financial year: April–March.

Support:
- month-by-month income
- salary
- bonus
- performance bonus
- incentives
- arrears
- freelance
- business
- rental
- interest
- other

Classifications:
- Guaranteed
- Expected
- Variable
- One-time

Appraisal UI:
- current take-home
- appraisal month
- expected hike %
- projected salary

Suggested screens:
- Yearly Overview
- Income Timeline
- Income Entry
- Appraisal Planner
- Annual Income Summary

Do not independently calculate authoritative annual totals in Angular.

Exit: annual income can be viewed/edited through backend APIs.

---

# PHASE 4 — Accounts, Categories, Transactions UI

Build:
- accounts
- categories
- add expense
- add income
- transaction history
- filters/date selection
- notes/tags
- optional goal selection
- fast Quick Add flow

Use Indian-first categories from the requirements for household, transport, family and financial spending.

Credit-card UI must distinguish:
- credit-card purchase = expense
- credit-card bill payment = settlement/transfer

Do not display a settlement as a second expense when backend identifies it as settlement.

Exit: transaction CRUD and Quick Add work.

---

# PHASE 5 — Monthly Money Plan UI

Input:
- monthly take-home income
- Conservative
- Balanced
- Aggressive

Prefer selectable cards instead of a plain dropdown.

Main dashboard title:
`Your Monthly Money Plan`

Show:
- Essentials / Needs
- Emergency / Safety
- Investments

Each shows amount, percentage and short explanation.

Balanced ₹1,00,000 reference:
- Needs ₹55,000
- Emergency ₹10,000
- Investments ₹35,000

These values must come from the backend response.

## Investment Allocation
Show:
- Equity
- Debt
- Commodities
- Crypto

Use cards, amounts, percentages, risk indicators and donut chart.

Crypto is optional/high-risk and must not be presented as mandatory or safe.

## Equity Roadmap
Show:
- Large Cap
- Mid Cap
- Small Cap

## Debt
Show:
- Liquid / Overnight
- Short Duration / TMF
- Target Maturity

## Gold & Silver
Show:
- Gold
- Silver

## Goal-Based Family Plan
Show:
- Child Education
- Retirement
- Home Purchase
- Vacation

IMPORTANT:
`Asset allocation = where money is invested`
`Goal allocation = what the money is intended for`

Do not present goals as another asset-class layer.

## Complete Monthly Split
Show income, needs, emergency, investments, asset breakdowns, equity/debt/commodity breakdowns and goals in a clean shareable layout.

Exit: backend values render correctly and no duplicate frontend financial engine exists.

---

# PHASE 6 — Safe-to-Spend + Recurring Expenses UI

Build:
- Safe to Spend Today
- remaining monthly budget
- spent amount
- upcoming recurring commitments
- remaining days
- recurring expense management

Backend owns the Safe-to-Spend formula. Angular only displays the result.

Exit: daily spending view is coherent and handles loading/error states.

---

# PHASE 7 — Bills + Credit Cards UI

Bills:
- upcoming bills
- due dates
- paid/unpaid
- amount
- recurring status

Credit cards:
- card
- limit
- used
- available
- statement date
- due date
- outstanding

Use clear, non-alarming warning states.

---

# PHASE 8 — Goals + Sinking Funds + Emergency Fund UI

Goals show:
- target
- current progress
- target date
- monthly contribution
- progress bar

Sinking funds support future planned expenses such as:
- insurance
- school fees
- Diwali
- vehicle service
- property tax
- vacation

Emergency fund can show:
- 3 months
- 6 months
- 9 months
- 12 months

Use backend-provided calculations.

---

# PHASE 9 — Yearly → Monthly → Daily Dashboard

Create the hierarchy:

`YEARLY → Where is my money going?`
`MONTHLY → How should I manage this month?`
`DAILY → What can I safely spend today?`

Enable drill-down:
Year → Month → Transactions/Daily detail.

Do not duplicate financial logic in the UI.

---

# PHASE 10 — Family Mode + Sharing

Build:
- household member views
- family dashboard
- shared planning
- partner sharing

WhatsApp summary must use backend values and include:
- Monthly Income
- Essentials / Needs
- Emergency / Safety
- Investments
- Investment Split
- Goal Split

---

# PHASE 11 — Net Worth + Indian Financial Accounts

Show assets:
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
- Other liabilities

Display:
`Net Worth = Assets - Liabilities`

Do not claim live market values without a verified backend integration.

---

# PHASE 12 — AI Money Assistant UI

Architecture:
`Angular → Spring Boot AI endpoint → FreeLLMAPI`

Provide actions such as:
- explain spending
- summarize budget
- explain safe-to-spend
- identify areas to review
- explain current plan

Never put provider/API keys in Angular and never call FreeLLMAPI directly from the browser.

AI is assistance, not guaranteed financial advice.

---

# PHASE 13 — Privacy / Security UI

Implement supported:
- logout
- session expiry handling
- privacy controls
- export/delete flows if backend supports them
- safe error messages

Never display secrets or internal stack traces.

---

# PHASE 14 — Frontend Testing / Release Candidate

Test:
- routes
- forms
- validation
- API services
- loading/error states
- responsive/mobile UI
- charts
- accessibility
- primary user journeys

Critical journeys:
`Register → Login → Household`
`Income → Annual Plan`
`Monthly Income → Money Plan`
`Add Expense → Dashboard`
`Credit Card Purchase → Correct Expense`
`Credit Card Payment → No Double Count`
`Goal → Contribution → Progress`
`Yearly → Monthly → Daily`

---

# PHASE 15 — Android / Capacitor

Reuse Angular.

Do not create separate financial logic for Android.

Validate:
- touch targets
- keyboard behavior
- safe areas
- responsive charts
- mobile navigation

---

# PHASE 16 — Production UI

Prepare:
- production API base URL
- optimized assets
- loading performance
- accessibility
- public-page SEO basics where applicable
- no localhost URLs in production

---

# FRONTEND ↔ BACKEND SYNCHRONIZATION

For every phase:
1. Backend publishes/updates API contract.
2. Frontend consumes that contract.
3. Frontend never guesses fields.
4. If required API data is missing, STOP and report it.
5. Do not silently change backend behavior.
6. Keep DTOs/interfaces, enum values and naming synchronized.
7. Render backend validation errors clearly.
8. Never replace backend financial values with frontend calculations.

Recommended execution order:
`Backend phase → Backend tests/contract → Frontend phase → Integration verification`

---

# PHASE COMPLETION REPORT

Every phase must report:

`PHASE:`
`STATUS:`
`Implemented:`
`Routes/components/services changed:`
`API endpoints consumed:`
`Models/interfaces changed:`
`Tests added/executed:`
`Build result:`
`Backend dependency:`
`Backend changes required:`
`Known issues:`
`Assumptions:`
`Next phase:`

Never silently continue into the next phase.

# STOP CONDITIONS

STOP and report if:
- backend API is ambiguous
- a financial rule is missing
- endpoint response differs from contract
- frontend would need to own a financial calculation
- backend modification is required during UI-only work
- a security/privacy decision is unclear

# GOLDEN RULE

The frontend presents.
The backend calculates.
The database persists.
The AI assists.
The user remains in control.
