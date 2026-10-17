# Budgeting App — Detailed Project & Feature Requirements

**Project working name:** Indian Family Money Planner  
**Product type:** Mobile-first family budgeting, salary planning and financial organization app  
**Primary audience:** Indian individuals, couples and families  
**Status:** Planning / pre-development  
**Source basis:** Original `Pasted markdown.md` prompt + subsequent product decisions

---

## 1. Product Vision

Build a practical Indian family money-management app that works across three levels:

1. **Yearly** — plan the financial year and expected salary/income.
2. **Monthly** — allocate and control the current month's money.
3. **Daily** — track actual spending and know what can safely be spent.

Core product promise:

> **Yearly = Where should my money go?**  
> **Monthly = How should I manage this month's money?**  
> **Daily = What can I safely spend today?**

The app must feel like a premium money-planning product, not a spreadsheet or a simple percentage tool.

### Important UI rule from the original prompt

The visible application UI must not use the word **"calculator"** in headings, buttons, cards, labels, result sections or other visible product copy. Use terms such as Planner, Money Plan, Budget, Family Finance, Financial Plan, Smart Money System, etc.

---

# 2. Operating Modes

## 2.1 Smart / Both Mode — Recommended Default

Combines:

- Yearly Financial Plan
- Monthly Budget
- Daily Expense Tracking

The yearly plan provides the target; monthly and daily activity measure actual performance against that target.

## 2.2 Monthly Budget Mode

For users who only want month-to-month management.

Core functions:

- Monthly income
- Expenses
- Fixed expenses
- Variable expenses
- Bills
- Recurring expenses
- Investments
- Goals
- Safe-to-Spend
- Monthly review

## 2.3 Yearly Salary Budget Mode

For users who want to plan the complete Indian financial year.

Default financial year concept:

- April → March

Also allow Calendar Year mode later:

- January → December

The yearly mode must not assume annual income = monthly salary × 12.

It must support month-specific income.

---

# 3. Yearly Salary / Income Planning

## 3.1 Income Sources

Support:

- Monthly salary
- Bonus
- Performance bonus
- Incentives
- Arrears
- Freelance income
- Business income
- Rental income
- Interest income
- Other income

Each income entry should have:

- Amount
- Expected month
- Income source
- Guaranteed / Expected / Variable / One-time classification
- Optional notes

## 3.2 Salary Appraisal Planning

Optional yearly planning fields:

- Current monthly take-home
- Appraisal month
- Expected hike percentage
- New expected monthly income

Example:

April–September: ₹50,000/month  
October–March: ₹60,000/month

The annual forecast must calculate using actual month-by-month expected income.

## 3.3 Annual Income Dashboard

Show:

- Guaranteed annual income
- Expected annual income
- Variable income
- One-time income
- Total planned annual income
- Monthly income timeline

Do not treat uncertain bonus/incentive income as guaranteed spending capacity.

---

# 4. Monthly Income Planning

Primary input:

- Monthly take-home income

Optional:

- Secondary household income
- Bonus
- Other monthly income

Indian Rupee formatting must be used throughout.

Example:

`₹1,00,000`

---

# 5. Allocation Styles

Original prompt defines three starting allocation styles.

## Conservative

- Needs = 60%
- Emergency / Safety = 10%
- Investments = 30%

## Balanced

- Needs = 55%
- Emergency / Safety = 10%
- Investments = 35%

## Aggressive

- Needs = 50%
- Emergency / Safety = 10%
- Investments = 40%

These are **starting templates**, not universal financial advice.

The product should eventually allow users to customize percentages.

---

# 6. Daily Expense Tracking

## 6.1 Quick Add Expense

Target: expense entry in approximately 5 seconds.

Minimum fields:

- Amount
- Category
- Subcategory
- Date
- Payment method
- Family member / spender
- Optional note

## 6.2 Payment Methods

Indian-first:

- UPI
- Cash
- Credit Card
- Debit Card
- Bank Transfer
- Wallet
- Other

UPI should be treated as a first-class payment method.

## 6.3 Indian Expense Categories

### Household

- Groceries
- Milk
- Vegetables / Fruits
- Domestic Help
- Electricity
- Water
- LPG
- Broadband
- Mobile Recharge
- Maintenance
- Household Supplies

### Transport

- Petrol
- Diesel
- CNG
- EV Charging
- Auto / Rickshaw
- Cab
- Metro
- Bus
- Parking
- FASTag / Toll
- Vehicle Maintenance

### Family

- Parents
- Children
- School Fees
- Tuition / Coaching
- Medical
- Medicines
- Festivals
- Gifts
- Family Functions

### Financial

- EMI
- SIP
- Insurance
- EPF
- PPF
- NPS
- RD
- FD
- Credit Card Payment

Categories must be configurable later.

---

# 7. Safe-to-Spend

A major daily-use feature.

The app should calculate an estimated daily spending capacity using:

- Current available variable budget
- Remaining days in the selected period
- Upcoming committed payments
- Existing spending
- Planned savings / investments

The result should be presented as guidance, not a guaranteed financial recommendation.

Example:

> **Safe to Spend Today: ₹850**

The calculation logic must be explicitly documented before implementation.

---

# 8. Fixed vs Variable Expenses

Separate:

### Fixed / Committed

Examples:

- Rent
- EMI
- School fees
- Insurance
- SIP
- Broadband
- Subscriptions

### Variable

Examples:

- Groceries
- Dining
- Shopping
- Entertainment
- Fuel
- Travel

### Flexible / Free Money

Money remaining after planned obligations and goals.

---

# 9. Recurring Expenses

Support recurring schedules:

- Daily
- Weekly
- Monthly
- Quarterly
- Half-yearly
- Yearly

Examples:

- Rent
- SIP
- Insurance
- Internet
- OTT
- Gym
- School fees
- Loan EMI

Recurring entries should generate planned occurrences rather than blindly creating duplicate actual transactions.

---

# 10. Bill Calendar

Show upcoming:

- Rent
- Electricity
- Mobile
- Broadband
- EMI
- Credit card due date
- Insurance
- SIP
- Subscription
- School fees
- Other recurring bills

Each bill should have:

- Name
- Amount
- Due date
- Frequency
- Account/payment method
- Reminder status
- Paid/unpaid state

---

# 11. Credit Card Management

Credit-card-specific fields:

- Card name
- Credit limit
- Statement generation date
- Payment due date
- Current outstanding
- Amount used
- Available limit
- Optional minimum due
- Payment history

The app should distinguish:

- Card transaction
- Credit-card bill/payment

Avoid double-counting a card purchase as a new expense when the credit-card bill is later paid.

---

# 12. Sinking Funds

For predictable non-monthly expenses.

Examples:

- Diwali
- Wedding / family functions
- Annual insurance
- Vehicle service
- School admission
- Property tax
- Vacation
- Medical reserve
- Annual subscriptions

Each sinking fund:

- Goal amount
- Target date
- Current balance/contribution
- Required monthly contribution
- Progress percentage

---

# 13. Emergency Fund

User chooses target:

- 3 months
- 6 months
- 9 months
- 12 months
- Custom

Track:

- Target amount
- Current emergency savings
- Monthly contribution
- Progress
- Months covered

The original 10% emergency allocation should be treated as a planning template, not a permanent rule for every user.

---

# 14. Goals

Support custom family goals.

Default examples:

- Child Education
- Retirement
- Home Purchase
- Vacation
- Car
- Bike
- Marriage
- Parents' Medical Reserve
- Laptop / Electronics
- Emergency Fund

Goal fields:

- Goal name
- Target amount
- Target date
- Current amount
- Priority
- Monthly contribution
- Optional linked account/investment
- Status

---

# 15. Critical Investment / Goal Logic Decision

The original prompt currently allocates the same investment amount twice:

1. Asset allocation:
   - Equity
   - Debt
   - Commodities
   - Crypto

2. Goal allocation:
   - Child Education
   - Retirement
   - Home
   - Vacation

These must NOT be treated as two separate 100% allocations of the same money.

Correct architecture:

### Dimension A — Asset Allocation

"Where is the money invested?"

Example:

- Equity
- Debt
- Gold/Silver
- Crypto

### Dimension B — Goal Tagging

"What is the money intended for?"

Example:

- Retirement
- Education
- Home
- Vacation

A single investment can be linked/tagged to a goal.

Example:

`₹10,000 SIP → Equity → Retirement`

`₹8,000 SIP → Debt → Education`

This prevents double counting.

---

# 16. Investment Allocation Templates

Original prompt:

### Conservative

- Equity 40%
- Debt 50%
- Commodities 8%
- Crypto 2%

### Balanced

- Equity 60%
- Debt 30%
- Commodities 8%
- Crypto 2%

### Aggressive

- Equity 70%
- Debt 20%
- Commodities 8%
- Crypto 2%

Important product decision:

**Crypto must be optional and not forced.**

Recommended default:

- Crypto Off / 0%

If enabled:

- Clearly mark as high risk
- User explicitly opts in
- Do not represent it as required for a balanced/aggressive plan

Templates should be editable.

---

# 17. Net Worth

Later-phase feature.

Assets:

- Bank accounts
- Cash
- FD
- RD
- Mutual funds
- Stocks
- EPF
- PPF
- NPS
- Gold
- Other assets

Liabilities:

- Home loan
- Car loan
- Personal loan
- Education loan
- Credit card outstanding
- Other debt

Formula:

`Net Worth = Total Assets - Total Liabilities`

---

# 18. Indian Financial Accounts

Later-phase support:

- EPF
- PPF
- NPS
- FD
- RD
- Mutual Funds
- Stocks
- Gold
- Silver

Do not connect to bank credentials in the initial version.

---

# 19. Annual Expense Planning

Yearly view must show:

- Month-by-month income
- Fixed expenses
- Variable expenses
- Investments
- Goals
- Sinking funds
- Emergency fund
- Expected surplus

Highlight expensive months.

Example:

> October planned expenses are ₹38,000 higher than your normal monthly average.

---

# 20. Yearly → Monthly → Daily Flow

The user should be able to drill down:

`Financial Year`
→ `Month`
→ `Week`
→ `Day`

Example:

Annual Plan:
- Income ₹8,40,000
- Expenses ₹4,20,000
- Investments ₹1,80,000
- Goals ₹1,20,000
- Buffer ₹1,20,000

October:
- Income ₹70,000
- Expenses ₹38,500
- Investments ₹15,000
- Goals ₹10,000

Today:
- Safe to Spend ₹850

---

# 21. Monthly Review

At month end show:

- Income
- Actual spending
- Budgeted spending
- Investments
- Savings
- Goal contributions
- Top categories
- Overspending
- Under-spending
- Month-over-month comparison
- Next-month suggestions

---

# 22. Yearly Financial Health

Later feature.

Potential indicators:

- Annual plan progress
- Spending pace
- Savings rate
- Investment rate
- Emergency fund progress
- Goal progress
- Debt burden
- Budget adherence

Example:

`Annual Budget Health: 82/100`

Every score must have transparent rules documented in code/requirements. No opaque AI-generated score.

---

# 23. Family Mode

Support:

- Household
- Members
- Individual spending
- Household income
- Member contributions
- Optional allowances

Example:

- Partner A income
- Partner B income
- Shared expenses
- Individual expenses

Privacy permissions should be considered before implementing shared accounts.

---

# 24. AI Money Assistant — Later Phase

Use FreeLLMAPI as a provider abstraction, not as the financial source of truth.

Potential questions:

- "Can I afford a ₹25,000 phone this month?"
- "Where did I overspend?"
- "Why am I running out of money before the 25th?"
- "Can I take a ₹10,000 vacation next month?"
- "What changed compared with last month?"

AI must NOT invent transaction data or calculations.

Rules:

1. Financial calculations are performed by deterministic application code.
2. AI receives structured summaries where possible.
3. AI must not fabricate balances.
4. AI must clearly distinguish facts from suggestions.
5. AI financial output must include appropriate educational/disclaimer language where relevant.
6. Do not send unnecessary raw financial data to external LLM providers.

---

# 25. Privacy & Security

Financial data is sensitive.

Requirements:

- Never store raw bank passwords.
- Never ask users for internet banking credentials.
- Authentication must be separated from financial data.
- Passwords must be hashed.
- Sensitive data must be protected in transit using HTTPS.
- API must enforce authorization.
- Users must only access their own household/data.
- Keep secrets out of source code.
- Use environment variables / secret management.
- AI requests should use minimum necessary context.
- Provide data export.
- Provide account/data deletion capability.
- Provide backup/restore strategy.

---

# 26. Sharing

Original prompt includes WhatsApp-friendly sharing.

Keep:

- Share monthly summary
- Share selected plan
- Partner-friendly summary

Do not expose private transaction-level information unless explicitly selected.

---

# 27. UI / UX

Style:

- Mobile-first
- Premium
- Warm
- Clean
- Family-focused
- Indian Rupee formatting
- Large readable numbers
- Rounded cards
- Soft shadows
- Progress bars
- Donut charts
- Minimal tables
- Friendly icons
- Responsive desktop layout

Original visual direction:

- Warm off-white / cream background
- Deep navy / charcoal text
- Gold accents
- Green for healthy states
- Amber for caution
- Red only for high-risk/caution

---

# 28. Suggested Main Navigation

1. Home
2. Transactions
3. Budget
4. Goals
5. More

Possible More items:

- Bills
- Recurring
- Credit Cards
- Sinking Funds
- Investments
- Net Worth
- Family
- Settings
- Data Export

---

# 29. V1 / V2 / V3 Scope

## V1 — Core Usable Product

Must include:

- User/local profile
- Monthly income
- Yearly salary planning foundation
- Monthly budget
- Expense tracking
- Indian categories
- Fixed/variable expenses
- Safe-to-Spend
- Recurring expenses
- Bill calendar
- Goals
- Family members foundation
- Credit cards
- WhatsApp sharing
- Monthly review
- Local/remote database persistence
- Authentication
- Basic privacy/security

## V2 — Financial Intelligence

- EPF
- PPF
- NPS
- Net worth
- Sinking funds
- Advanced yearly planner
- Salary appraisal planner
- AI Money Assistant
- Tax planner
- CSV import
- Advanced reports
- Forecasting

## V3 — Automation / Integrations

- Bank / Account Aggregator integrations
- Automatic transaction import
- Smart categorization
- UPI-related automation where legally/technically appropriate
- AI financial insights
- Advanced family sharing
- Offline-first sync
- Advanced notifications

---

# 30. Non-Goals for Initial Release

Do NOT attempt in V1:

- Direct bank credential storage
- Automatic bank scraping
- Full investment brokerage integration
- Guaranteed-return recommendations
- Autonomous financial transactions
- Complex tax filing
- Complex portfolio optimization
- AI-generated financial calculations
- Cryptocurrency trading
- Loan origination

---

# 31. Definition of Done

A feature is not complete until:

1. Requirement exists in this document.
2. Data model is defined if data is involved.
3. API contract is defined if backend is involved.
4. UI behavior is defined.
5. Validation is implemented.
6. Error states are implemented.
7. Unit tests exist for calculation/business logic.
8. Integration/API tests exist where appropriate.
9. Responsive behavior is verified.
10. Existing functionality is not broken.
11. Build succeeds.
12. Developer/agent reports exactly what changed.

---

# 32. Source-of-Truth Rule for AI Coding Agents

When using FreeLLMAPI + Codex/Devin:

**This file is the product requirement source of truth.**

Agents must:

- Read the relevant requirement before coding.
- Never invent missing business rules.
- Never silently change percentages/formulas.
- Never modify out-of-scope features.
- Ask for clarification when two requirements conflict.
- Reuse existing architecture instead of creating duplicate systems.
- Run tests after implementation.
- Report assumptions explicitly.
- Report files changed.
- Report tests executed and results.
