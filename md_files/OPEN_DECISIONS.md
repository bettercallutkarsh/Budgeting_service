# Open Product / Database Decisions

These items are deliberately visible so the data model does not silently invent requirements. Resolve them before generating final DDL for the affected feature. Recommended defaults are recommendations only, not approved product behaviour.

## DBD-001 — Safe-to-Spend formula

**Status:** Must resolve before implementing Safe-to-Spend.  
**Why it matters:** Formula determines what is shown as safe to spend and which fields/periods are inputs.

The architecture document lists a conceptual sequence: current period income → committed upcoming payments → planned savings/investments → already spent → flexible money → remaining days. That is not a fully testable formula.

Decisions needed:
- Does the starting point use opening available cash/bank balance, income received, planned income, or some combination?
- Are uncertain/variable/one-time expected receipts excluded from safe capacity until received?
- How are unpaid bills/commitments reserved, and how do we prevent a paid/posted bill from being deducted twice?
- Are savings/investment allocations reserved at start of month or based on actual transfer/transaction status?
- How do credit card purchases and card payments affect the figure?
- How do mid-month onboarding, negative remaining amount, last day of month and changed budgets work?

**Recommended process:** agree a written equation and 5–10 numeric examples before implementation. Do not infer from screen labels alone.

## DBD-002 — Crypto disabled by default vs legacy 2% template

**Status:** Must resolve before allocation seed rows are created.  
**Conflict:** Legacy allocation presets include Crypto 2% and Commodities 8%, while project direction says Crypto is optional, high-risk and default-off.

Options:
1. Crypto disabled and the remaining enabled asset class percentages are explicitly renormalized to 100% by an approved policy.
2. Crypto disabled and the 2% goes to a specifically approved bucket.
3. Keep legacy preset as a reference only, but offer a separate default-off preset with approved percentages.

**Recommendation:** have a clearly named default-off preset whose enabled classes total 100%; do not silently renormalize behind the user's back. Product owner must choose exact percentages.

## DBD-003 — Goal contribution semantics

**Status:** Must resolve before goal progress is implemented.  
Should a contribution represent (a) a plan amount, (b) a non-cash earmark of existing funds, or (c) an actual asset/cash movement? Those are different concepts.

**Recommended split:** monthly goal allocation is planning; actual cash/asset change is recorded by the account/transaction/investment domain; a link/earmark connects the asset to a goal without adding it twice to net worth. V1 can track goal planning and manually recorded earmarks while clearly labelling the progress source.

## DBD-004 — Household privacy model

**Status:** Resolve before final authorization implementation.  
Can household members see all household transactions? Can an account be private to its owner? Which roles can invite/remove members, edit budgets, post/void transactions, export data or delete a household?

**Recommended default:** explicit roles and a default shared/private scope per account; backend authorization checks are required for every API call, not just UI hiding.

## DBD-005 — V1 currency scope

**Status:** Recommended decision.  
**Recommended V1:** one currency per household (default INR), persist currency code for money rows, reject cross-currency transfers and FX reporting until rate/source/rounding behaviour is designed. This is safer than pretending multi-currency reporting works because a `CHAR(3)` column exists.

## DBD-006 — Income planning and plan versioning

**Status:** Confirm during design review.  
Can a household create alternative yearly scenarios or multiple saved versions? Can a finalized monthly plan be revised while retaining what the user originally planned?

**Recommended default:** support yearly plan versions/scenarios, use one selected primary plan per household/period, and preserve finalized allocation template versions. Actual financial transactions remain shared facts, not copied per plan.

## DBD-007 — Allocation customization

**Status:** Confirm before templates are seeded.  
Are allocation template edits global/admin-controlled, copied into household-owned templates, or both? May a user change a template after using it? Which allocation groups appear in V1?

**Recommended default:** system templates versioned and immutable after use; household custom templates stored separately; a monthly plan preserves a snapshot of applied values.

## DBD-008 — Account opening balance and card liability

**Status:** Confirm with transaction contract.  
The opening balance of a credit card means outstanding liability, while a bank account opening balance means asset cash. The service needs a precise sign/effect model for every account type and transfer direction.

**Recommended default:** store positive absolute starting amount and define balance semantics per account type in backend code/tests; don't expose one generic signed math rule without documenting the account kind.

## DBD-009 — Bill payment and overpayment policy

**Status:** Resolve before bills API.  
Support partial payments in V1? What happens if payment exceeds amount due (credit balance, refund, reject)? Can one payment be allocated across multiple bills?

**Recommended default:** multiple partial payments per occurrence are supported; reject overpayment in V1; one payment row references one transaction or one transfer. Add split payment allocations only if the product requires one financial payment across multiple bills.

## DBD-010 — Recurrence edge cases

**Status:** Resolve before recurrence jobs.  
For monthly recurrence on day 29/30/31, use last day of shorter month or skip? Which timezone determines occurrence generation? Should recurrence create reminder, draft or posted actual event?

**Recommended default:** reminder/draft only, never automatically posted actual; choose last-valid-day behaviour for month-end dates; unique rule/date key makes retries idempotent.

## DBD-011 — Authentication and sessions

**Status:** Confirm before authentication DDL.  
Is V1 email + password only, or also Google/OAuth? JWT stateless auth or persisted sessions/refresh tokens? What are account deletion and password-reset flows?

**Recommended default:** keep credential data separate from profile; hash passwords; add provider identity/session tables only when auth design requires them. Never store raw banking credentials.

## DBD-012 — Sinking funds vs goals

**Status:** V2 design decision.  
Sinking funds reserve money for known future expenses (insurance, Diwali, school fees, service); goals may target accumulated wealth (retirement/home). Decide whether these can share one goal model or need a dedicated planned-spend/drawdown lifecycle.

## DBD-013 — Credit-card statement reconciliation

**Status:** V1 scope decision.  
Is a statement total and due date enough for V1, or are statement-line imports, matching card transactions, disputes, interest/fees and reconciliation required immediately?

**Recommended default:** statement summary only in V1; add statement-line table/import workflow later. Card fees/interest, when recorded, are actual expense transactions.

## DBD-014 — Catalog administration

**Status:** Confirm before catalog API.  
Can an administrator edit catalog choices at runtime, or are reference values shipped through Flyway only? Which labels require Hindi/other locales?

**Recommended default:** stable machine codes, migration-managed system values, active/inactive state; runtime administration only when actual product needs it.

## DBD-015 — Audit and data deletion retention

**Status:** Confirm before production launch.  
Which sensitive user actions must be audited, for how long, and how does account deletion interact with financial history and legal/retention constraints?

**Recommended default:** minimum useful audit fields, sanitized metadata only, no secrets, explicit retention and deletion/anonymization policy reviewed before production.

---

## Review workflow

1. Mark each decision `APPROVED`, `DEFERRED`, or `NEEDS CLARIFICATION`.
2. For approved choices, record the selected option and a short reason.
3. Update `DATABASE_DESIGN.md`, `DATABASE_ERD.md`, `FINANCIAL_BUSINESS_RULES.md` and `FLYWAY_PLAN.md` together where affected.
4. Only after the affected decisions are resolved should executable DDL be generated.
