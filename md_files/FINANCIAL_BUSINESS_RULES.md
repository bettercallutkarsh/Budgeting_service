# Financial Business Rules — Data Layer Contract

**Status:** Draft rules for design review. Unresolved product logic is explicitly marked; do not code formulas marked pending.

## 1. Source-of-truth boundaries

- Actual posted transactions/transfers represent actual movement/activity.
- Planned income entries, allocation plans, recurring rules, recurring occurrences and bill occurrences represent expectations/commitments, not actual money movement.
- Backend domain services own calculations and validations. Angular displays API results; AI can explain structured results but is never authoritative for arithmetic.
- Use Java `BigDecimal` for money; define one rounding/reconciliation policy and automated tests.

## 2. Money and date rules

- Every stored amount has a known currency context. V1 is recommended to operate in one household currency (default INR), while storing `currency_code` where money is recorded for future extension.
- V1 rejects cross-currency transfers; FX amount/rate/settlement behaviour is a future design item.
- Use `DATE` for business dates and `DATETIME(6)` for event timestamps. Document and consistently apply UTC storage for timestamps; use household/user timezone for UI reminders and “today” boundaries.
- Suggested `DECIMAL(19,4)` persistence precision, with approved INR currency-scale rounding on posted monetary events. Rounding residuals in allocation child rows must be reconciled deterministically (e.g. assign residual to a deterministic final eligible child) and tested.
- Amounts in transaction headers and split/payment rows are positive absolute values; type/status controls their meaning. Do not mix signed amounts and separate type values inconsistently.

## 3. Actual transaction rules

### 3.1 Transaction categories

- `transactions` is the event header: household, account, type, date, amount, currency, payee/member/source metadata and lifecycle status.
- `transaction_splits` is the only category attribution source. A simple transaction has one split; a split transaction can allocate to multiple categories.
- For posted income/expense/refund transactions, sum of split amounts must equal header amount. Validate in the same DB transaction before posting and revalidate on mutation.
- Only posted transactions appear in actuals. Draft does not count; void transactions are excluded but retain audit history.
- An expense refund offsets spending in the original category when linked to the original expense; it is not ordinary earned income. Unlinked refunds require a documented categorization path.
- Category must match transaction kind (income category for income, expense category for expense/refund) as appropriate.

### 3.2 Transfer rules

- A transfer has source account, destination account, date and principal amount; source and destination cannot be the same account.
- A transfer's principal never counts as spending/income in household cash-flow reporting.
- Transfers between user-owned asset accounts preserve household net worth except for separately modelled fees. Credit-card bill payment is a settlement that reduces bank cash and reduces card liability; it does not add another purchase expense.
- Any transfer fee is treated once as a separate expense transaction or another explicitly approved fee treatment; do not include fee in the principal and also expense it.
- V1 cross-currency transfer is rejected.

### 3.3 Account balance semantics

- Opening balance is a dated starting point. For asset-like accounts, it represents available asset balance; for credit-card accounts, it represents opening amount owed. Account-type-specific effects are defined in service code and tested.
- Current balance, card outstanding, amount used and available credit are derived from opening snapshot plus posted transactions/transfers/statements under domain rules. They are not directly editable values.
- Once posted activity exists, do not change an account's currency/type/opening balance without an audited migration/correction flow.

## 4. Income and yearly/monthly planning

- Income source is a named origin (salary, bonus, freelance, rental etc.), not proof an amount was received.
- `planned_income_entries` are dated expectations. They may be guaranteed, expected, variable or one-time. Uncertain income must not be treated as guaranteed spend capacity.
- Actual income is a posted `transactions` record. A planned item can link to one actual transaction; do not count the planned amount and actual amount as two separate income events.
- A month can have a different expected income amount from every other month. Do not infer annual income as monthly salary × 12.
- For the default Indian FY, start date is 1 April and end date is 31 March in the following calendar year. Store actual dates; do not hardcode one FY year. Calendar-year planning is a distinct supported/future period basis.
- Monthly plan rows are children of a specific yearly-plan version; yearly and monthly dashboards aggregate underlying monthly/actual activity, not duplicate actual transactions.
- Plan version/template used by a finalized plan should be traceable. Prefer new template versions over mutating a template used by historical plans.

## 5. Allocation rules

- Primary allocation levels in current project requirements:
  - Conservative: Needs 60%, Emergency/Safety 10%, Investments 30%.
  - Balanced: Needs 55%, Emergency/Safety 10%, Investments 35%.
  - Aggressive: Needs 50%, Emergency/Safety 10%, Investments 40%.
- These are starting templates, not personalized financial advice; user customization is a product direction.
- Asset allocation is a “where invested?” dimension; goal allocation is “what for?” dimension. Do not sum both dimensions as independent cash outflows.
- The current service prompt records these starting sub-allocation templates:
  - Asset classes: Conservative Equity 40%, Debt 50%, Commodities 8%, Crypto 2%; Balanced 60%, 30%, 8%, 2%; Aggressive 70%, 20%, 8%, 2%.
  - Equity: Conservative Large/Mid/Small 70/25/5; Balanced 60/30/10; Aggressive 50/35/15.
  - Debt: Conservative Liquid/Short Duration/Target Maturity 40/40/20; Balanced 30/45/25; Aggressive 20/40/40.
  - Commodities: Conservative Gold/Silver 85/15; Balanced 80/20; Aggressive 75/25.
  - Goal dimension: Conservative Education/Retirement/Home/Vacation 45/35/15/5; Balanced 40/40/15/5; Aggressive 35/45/15/5.
- Crypto is optional and default-off per project direction. The above legacy asset-class preset includes 2% crypto, which conflicts with default-off unless the remaining categories are intentionally renormalized or that 2% is reallocated. Do not seed final allocation rows until the decision in `OPEN_DECISIONS.md` is approved.
- Every enabled allocation group must sum to exactly 100% of its own defined base amount. Example: asset class sums to investment amount; equity subclasses sum to equity amount; debt subclasses sum to debt amount; commodity subclasses sum to commodity amount.
- Goal allocation is an independent tagging/planning view; it must not be added again to investment cash outflow.

## 6. Budgets and Safe-to-Spend

- A budget is a planned limit for a category and month. Use first day of month as `period_month`; enforce one active budget per household/category/month in V1.
- Spent amount is computed from posted expense splits in that category/month, net of qualifying refunds; draft/void excluded.
- If rollover is enabled, carryover rules must be specified (positive rollover only? expiry? negative overspend carry?) before coding.
- The current architecture describes Safe-to-Spend conceptually as period income minus commitments, planned savings/investments and already-spent amounts, then spread guidance over remaining days. The detailed feature requirements do not give a fully testable formula.
- Safe-to-Spend formula is therefore **PENDING PRODUCT APPROVAL**. Before implementation define: opening/carry-in funds, whether income is expected vs received, treatment of variable bonuses, what counts as committed/unpaid, whether planned savings are reserved before/after actual spending, treatment of credit-card purchases, refunds, mid-month onboarding and negative balances. See `OPEN_DECISIONS.md`.

## 7. Recurrence rules and occurrences

- A recurring rule defines schedule and expected event. It is not an actual transaction.
- One occurrence per recurring rule/date; use a unique constraint and idempotent service logic so retrying a job does not duplicate occurrences or transactions.
- Occurrences should be reminders or draft proposals by default, not silently posted actual transactions.
- When user confirms the event, create/post a transaction or transfer and link it to the occurrence.
- Month end policy must be agreed for day 29–31, leap years, weekly weekday numbering, skipped periods, paused/ended rules and timezone-sensitive reminders.

## 8. Bills and payments

- Bill template defines a recurring pattern/defaults; bill occurrence is a particular due obligation; bill payments are actual payment allocations.
- Support partial payments: occurrence remains partially paid until successful payment total reaches the due amount. Paid/partial/overdue state should be derived from payment totals and current local date or consistently reconciled.
- A payment row links either one posted expense transaction or one transfer, not both. Service validates the total linked payments do not exceed the bill amount unless product explicitly supports credits/overpayment.
- Paying a credit-card statement via account transfer is a settlement, not another expense. Card purchase transactions are the expenses.
- Statement total/minimum due are snapshots from issuer/user input, not spend transactions.

## 9. Goals, emergency fund and sinking funds

- `goals` defines a target; `goal_contributions` records an explicitly classified plan/earmark/realization event.
- An earmark by itself does not move cash and must not create a transaction or increase net worth.
- If a cash/asset movement occurs, store that actual event in the transaction/investment/account domain and link it to the goal as appropriate. Net worth counts the underlying asset once.
- Do not store `goals.current_amount` as an independently editable second truth until contribution/asset-link semantics are finalized.
- Emergency fund target may be expressed as 3/6/9/12 months or custom; the calculation needs an approved essential-expense baseline and should not assume exactly 10% of income is always the right contribution.
- Sinking funds are currently V2. Before creating their tables, decide whether they are sufficiently similar to goals or require dedicated planned-expense/drawdown lifecycle.

## 10. Household ownership, history and privacy

- Every API read/write for household data checks authenticated membership and permission. Do not trust `household_id` supplied by the frontend.
- Cross-household account/category/payee/goal/member/transfer references are rejected.
- Archive/deactivate categories/accounts/catalog values once referenced; no cascading delete of posted financial history.
- Use audit logs for posting/voiding transactions, changes to permissions and other sensitive financial lifecycle changes.
- Do not store raw bank login credentials. Protect passwords using an approved password hashing function. Keep application/API provider secrets outside source control and away from frontend code.
- AI calls use only minimal structured summaries necessary for explanation; raw transaction lists are not sent unless explicitly required and approved.

## 11. Required test scenarios before DDL/API sign-off

1. One transaction with one split; one transaction with multiple splits; reject unequal sum.
2. Refund reduces the original category's spending correctly.
3. Draft and void transactions excluded from budget actuals.
4. Bank-to-bank transfer changes account balances but not household income/spending.
5. Credit-card purchase is counted once; bank-to-card settlement is not a second expense.
6. Partial bill payment then final payment; due/partial/paid state reconciles.
7. Recurrence worker retried twice without duplicate occurrences/transactions.
8. Salary changes mid-year and a one-off bonus only appears in the planned month.
9. April-to-March financial year and calendar-year dates are both computed correctly.
10. Goal tagging an investment does not double count invested assets or expenses.
11. Household A cannot reference/read/update household B account, category or transaction.
12. Allocation amount rounding totals reconcile exactly; crypto-off case follows approved policy.
13. Safe-to-Spend boundary cases: final day, negative amount, no income, irregular income, large upcoming bill, new user mid-month and budget modified after posting.
