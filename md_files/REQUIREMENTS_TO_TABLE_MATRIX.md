# Requirements-to-Table Traceability Matrix

**Goal:** demonstrate which domain tables support each project capability and identify business rules that require application/API logic in addition to database constraints.

## 1. V1 capability mapping

| Requirement / user journey | Primary tables | Important non-table rule / test |
|---|---|---|
| Register/login/profile | `users`, `user_credentials`, `user_preferences` | Password hash only; normalized email uniqueness; session strategy documented separately |
| Family/household | `households`, `household_members`, `household_invitations` (if sharing in initial release) | Household authorization on every query and mutation; member role matrix; private-vs-shared account policy |
| Dropdown/reference values | `t_catalog`, `t_catalog_code` | Stable codes; active values only for new selection; service validates expected catalog membership |
| Cash/bank/wallet accounts | `accounts` | Opening balance date and currency stable after posted activity; derived balance |
| Credit cards | `accounts`, `credit_card_details`, `credit_card_statements`, `transactions`, `transaction_splits`, `account_transfers`, `bill_payments` | Purchase is expense; payment is settlement/transfer; statement is a snapshot and not a new expense |
| Custom categories/subcategories | `categories` | Household-scoped, normalized names, parent category same household/type, archive rather than delete when referenced |
| Quick-add expense | `transactions`, `transaction_splits`, `accounts`, `categories`, `household_members`, `payees` | Positive absolute amount; category splits reconcile to header total; user is authorized for selected account |
| Income receipt | `transactions`, `transaction_splits`, `accounts`, `income_sources` | Actual receipt differs from expected income; do not count a planned entry as cash received |
| Refund/reversal | `transactions`, `transaction_splits` | Link to original when known; refund reduces spending rather than being reported as ordinary income; void/reversal auditability |
| Account transfer | `account_transfers`, `accounts` | Source differs from destination; no expense classification for principal; cross-currency rejected in V1 |
| Annual income forecasting | `financial_year_plans`, `planned_income_entries`, `income_sources` | Month-specific and one-off amounts; uncertain bonus not treated as guaranteed capacity |
| Salary appraisal planning | `financial_year_plans`, `planned_income_entries`, `income_sources` | Entries before/after appraisal can carry different amount/date; no assumption `annual = monthly × 12` |
| Yearly/monthly plan | `financial_year_plans`, `monthly_plans`, `planned_income_entries` | April–March default; actuals aggregate from transactions, not duplicated into plan records |
| Conservative/Balanced/Aggressive templates | `allocation_templates`, `allocation_template_items`, `monthly_plan_allocations` | Exact ratios reconciled at every allocation level using `BigDecimal`; crypto-off policy unresolved |
| Monthly category budgets | `budgets`, `categories`, `transaction_splits`, `transactions` | Spent derived only from posted transactions and refund/void policy; one budget per category/month in V1 |
| Fixed/variable/flexible spending | `categories.default_spending_behavior_code_id`, `transaction_splits.spending_behavior_code_id`, catalogs | Category value is only a default; split-level classification may override it |
| Safe-to-Spend Today | Transactions, splits, budgets, income plan entries, bill occurrences, monthly allocations | Exact formula is not fully specified; implementation blocked until `OPEN_DECISIONS.md` resolved; test month-end/negative/irregular income cases |
| Recurring expense/income | `recurring_rules`, `recurring_occurrences`, `transactions` or `account_transfers` | Schedule creates an expectation, not an actual; unique rule/date prevents duplicate generation; never auto-post without an explicit rule |
| Bills calendar | `bill_templates`, `bill_occurrences`, `bill_payments`, `notifications` | Partial payments allowed; overdue/partial/paid derived from dates and payment totals; duplicate reminder prevention |
| Goal management | `goals`, `goal_contributions`, `monthly_plan_allocations` | Goal allocation is an intent dimension, not a second expense or asset; define whether V1 tracks earmarks or realized asset value |
| Monthly review | Transactions, splits, budgets, planned income, monthly plans, goals, bill payments | All totals must be derived with one shared backend calculation/service layer |
| WhatsApp/shareable plan | Plans, allocations, budgets, summary APIs | Share selected summary only; never expose transaction-level data unless user explicitly opts in |
| Notifications/reminders | `notifications`, bill/recurring tables | Store recipient state separately from business facts; avoid sensitive lock-screen detail by default |
| Security/audit trail | Users, credentials, membership, `audit_logs` | Enforce authorization in backend; audit changes to posted/void transactions and membership/permission changes |

## 2. V2 / V3 mapping (planned, not a reason to create tables now)

| Requirement | Candidate future tables | Dependency/decision before design |
|---|---|---|
| Sinking funds | `sinking_funds` or an approved goal subtype/model | Decide whether sinking-fund lifecycle differs from goals, especially actual spending/drawdown |
| EPF/PPF/NPS/FD/RD/stocks/mutual funds | `investment_accounts`, `investment_holdings`, `investment_transactions` | Unit/value basis, valuations, data source, manual vs imported holdings |
| Goal tagging of holdings | `goal_asset_allocations` / link table | Multiple goals can share one holding; ensure no double-counting |
| Assets and liabilities | `assets`, `liabilities`, maybe account subtypes | Decide which are account balances vs manually tracked off-platform assets/debts |
| Net worth | `net_worth_snapshots` | Snapshot date, valuation provenance, market-value staleness policy |
| Tax planner | `tax_plans` and rule/version tables as justified | Tax-year basis, statutory rule versioning, disclaimers; do not hard-code year-specific rules as generic fields |
| CSV import | `import_jobs`, `import_rows` | Row-level validation, deduplication, review/commit workflow |
| Bank/Account Aggregator integrations | Integration connection/event tables as compliance review requires | Never store internet-banking passwords; consent and provider lifecycle need separate design |
| AI Money Assistant | Optional `ai_insights` | Minimize external context; AI is explanatory only; calculations from backend |
| Localization | `catalog_code_translations` | Add only when multiple locales are required |
| Receipt attachments | `transaction_attachments` and storage metadata | File storage, malware scanning, access rules, retention |
| Asynchronous export/deletion | `data_export_jobs`, `data_deletion_requests` | Add when actual workflow has asynchronous job/retry states |

## 3. Acceptance criteria for requirements coverage

Before migrations are approved, every V1 row must have:

1. A table/column or explicitly documented derived value supporting it.
2. API validation/authorization rules.
3. A test scenario for normal and boundary/error cases.
4. A statement showing how actuals differ from plans and snapshots.
5. A clear phase assignment (V1, V2, or V3).

A UI card alone is not evidence a database requirement is complete; a feature may rely on derived API output rather than its own table.
