# Monthly Budgeting Money Planner — Final Angular UI Requirements

**Document type:** Frontend source of truth for implementation  
**Version:** 1.0 — aligned with `SERVICE_REQUIREMENTS_FINAL.md`  
**Target stack:** Angular 22, standalone components, TypeScript strict mode, SCSS  
**Local project:** `D:\Budgeting_App\Budgenting_ui`  
**Backend:** Spring Boot API under `/api/v1`  
**Business timezone:** `Asia/Kolkata` (IST, UTC+05:30)  
**Primary currency:** INR (₹)

> This document defines a small, approachable individual budgeting app. It replaces the earlier broad UI plan for household finance, bills, goals, AI and investment valuation. Do not add screens for deferred features just because they appeared in older prompt documents.

## 1. Plain-language glossary

- **MVP (Minimum Viable Product):** the smallest useful working first version. It must solve the core job reliably, but can postpone advanced features.
- **Monthly income for budget:** the value the user entered as the basis for allocating that month's income.
- **Recorded income:** the user's recorded monthly amount. It is not automatically verified with a bank.
- **Credit-card outstanding:** an estimate from the opening amount, tracked credit-card purchases and payments the user entered. It is not a live issuer/bank balance.
- **Bucket:** one of Essentials, Safety or Investments.

Example to communicate plainly: if a user records ₹30,000 for October and the amount that actually reached their bank was ₹28,000, the app still uses the entered ₹30,000 until the user edits the current month's budget to ₹28,000. V1 does not verify salary credits automatically. Avoid wording such as “verified earnings.”

## 2. Experience goals

1. First-time setup should feel short and guided, not like filling a finance spreadsheet.
2. A user should be able to add a common expense quickly with amount, category, date/time and optional note.
3. Use everyday labels. Explain “Essentials,” “Safety” and “Investments” with one-line descriptions.
4. Show actual amounts alongside percentages; never make users calculate in their head.
5. Clearly distinguish expenses, money set aside for Safety, investment contributions and credit-card bill payments.
6. Make the app responsive on mobile, tablet and desktop.
7. Never make claims of guaranteed savings, investment returns or a precise “Safe to Spend Today” in V1.

## 3. Navigation and screen map

Use a small navigation structure:

1. **Home** — current-month summary and today's entries.
2. **Transactions** — entry history, filters, details and void/correction flow.
3. **Budget** — current/future monthly income and bucket allocations.
4. **Reports** — daily/monthly/quarterly/half-yearly/yearly/custom history.
5. **Profile & Settings** — name, email/phone, employment type, sign out, and credit-card opening outstanding/summary.

On desktop, a compact side navigation is acceptable. On mobile, use a bottom navigation or compact drawer with clear active state. Keep the main action “Add entry” easy to reach from Home and Transactions.

Do not add navigation for households, bills, goals, tax, net worth, investments portfolio valuation, AI assistant or recurring schedules in V1.

## 4. Visual design requirements

- Premium but calm: warm off-white/cream background, deep navy/charcoal typography, restrained green for positive budget status, amber for caution, red only for over-allocation or blocking errors.
- Cards may use rounded corners, subtle borders/shadows and ample whitespace. Avoid too many cards or dense tables.
- Numbers should be visually clear, with a consistent hierarchy: monthly income and remaining bucket amounts are primary; percentages are secondary.
- Use text + icon/badge for states; never communicate status using color alone.
- Use Indian number formatting: `₹1,00,000.00` when paise matter, or `₹1,00,000` for whole-rupee display. Do not round silently in API data.
- Date labels use a human-readable India format such as `18 Oct 2026`; time uses India local time and a consistent 12-hour or 24-hour format.
- Do not use “calculator” in user-facing headings, buttons, cards or labels. Use “Budget Planner”, “Monthly Plan”, “Your money” or “Budget progress”.

## 5. Screen 1 — Register, login and session states

### Register form

Fields: full name, email, Indian mobile number, employment type, password and confirm password.

- Phone UI may visually show `+91` as a fixed country prefix and accept the ten-digit mobile portion; API receives normalized E.164 `+91...`.
- Email trim/lowercase normalization should happen server-side too; frontend normalization is usability, not security.
- Employment types: Salaried, Student, Unemployed, Self-employed, Other.
- Password field has show/hide control, allows password managers/paste, and never logs or persists the password in local storage.
- Field errors appear next to fields and move focus sensibly when submission fails.
- On success, continue to monthly budget setup if none exists.

### Login

Email + password, loading state, generic “Email or password is incorrect” message, and a clear session-expired route. Do not reveal whether an email is registered. A password-reset workflow is a production release gate; if not implemented in the development MVP, do not show a fake “reset sent” interaction.

### Security states

- If API returns 401, preserve non-secret form contents where safe, clear any stale in-memory user state, and route to login with a short explanation.
- Protected route guards improve UX only; backend authorization remains the security boundary.
- Do not store session IDs, tokens or passwords in `localStorage`.

## 6. Screen 2 — First-run onboarding and monthly income

Use a short wizard or sequential cards:

1. Confirm profile details.
2. Ask current employment type.
3. Ask current month's income for budgeting.
4. Ask income type (`Salary`, `Pocket money`, `Other income`). Default suggestion from employment status but let user choose.
5. If Other income, show optional “Income source label” field (e.g. freelance).
6. Ask allocation mode and whether the user plans to include Investments.
7. Save and show a budget summary.

Display copy near income input: “Enter the amount you want to use as this month's budget basis. The app does not verify bank credits.” Keep that explanation short and available via info text. Do not call it “verified salary received.”

If income is not known yet, allow 0.00 or let the user skip budget setup; don't invent a default amount. A missing budget and a zero-income budget must render differently.

## 7. Screen 3 — Budget allocation

### Preset selection cards

Show the available choices and descriptions:

- **Conservative:** “For people or families who want higher stability, lower market risk and a stronger safety allocation.”
- **Balanced:** “A practical mix of everyday expenses, safety and long-term investment contributions.”
- **Aggressive:** “For people who want a larger investment allocation for the future and understand that investments can fluctuate.”
- **Manual:** “Set your own percentages.”

Avoid saying these presets are guaranteed suitable or personalized investment advice.

### Preset math

- Conservative = Essentials 60%, Safety 10%, Investments 30%.
- Balanced = Essentials 55%, Safety 10%, Investments 35%.
- Aggressive = Essentials 50%, Safety 10%, Investments 40%.

On selection, display each amount in INR and the percent next to it. The server response is authoritative.

### Manual mode validation

- Percent fields accept 0–100 inclusive and no more than 2 decimal places.
- Inline total indicator: “Total allocation: 100%” for valid; “Your percentages must total 100%” otherwise.
- Disable Save while invalid, but backend validation still must be handled.
- If a user changes any preset percentage, switch selected mode to Manual and make that change clear.
- Preserve form values if save fails; show the server error next to the relevant field.

### Investments OFF — approved Option C

- Do not automatically move the previous Investment percentage into Essentials or Safety.
- When user turns Investments OFF, set Investment to 0%, set mode to Manual, and prompt them to enter Essentials and Safety.
- Essentials + Safety must total exactly 100%.
- Example: 75% Essentials + 25% Safety + 0% Investments is valid. 70% + 20% + 0% is invalid.
- When turning Investments back ON, ask the user to choose a preset or set all three percentages. Do not silently restore old values.
- If the backend rejects turning Investments OFF because this month already has a posted investment entry, show a blocking explanation: “This month already has recorded investment contributions. To keep the history accurate, you can’t turn Investments off for this month. Void/correct the mistaken entry first, or keep Investments on.” Only offer the correction action if the entry is owned by the current user and actually correctable.
- If an investment entry attempt is rejected because the saved month has Investments OFF, show the server's explanation. Never rely only on hiding the action in UI.

### Amount mismatch prevention

- UI may preview allocations, but it must not use a separate inconsistent formula from Java.
- Prefer server-provided amount fields after save. For live preview, implement one shared, tested decimal/paise calculation utility that uses the exact same rounding contract: Essentials rounded HALF_UP to paise; Safety rounded HALF_UP when Investments ON; Investments is the residual so all three sum exactly to income. If Investments OFF, Essentials is rounded and Safety is the residual, Investments = 0.00.
- Always show a total line confirming bucket amounts sum to the monthly income. Never display a ₹0.01 mismatch.

## 8. Home dashboard

For selected/current month display:

1. Monthly recorded income.
2. Three bucket cards (Essentials, Safety, Investments): planned limit, used, remaining, and percent used.
3. Today's recorded expenses.
4. Month-to-date consumption expenses.
5. Safety savings and investment contributions as separate metrics.
6. Estimated combined credit-card outstanding (if card tracking is used).
7. Recent entries and “Add entry” action.

### Status wording

- `WITHIN_ALLOCATION`: “Within your monthly allocation so far.” Do not claim “You will definitely save more at month end.”
- `OVER_ALLOCATION`: “You’ve exceeded this bucket’s monthly allocation by ₹X.”
- `NO_INCOME`: “No income is recorded for this month. You can still view entries, but budget status is limited.”
- `BUDGET_NOT_SET`: “Your budget isn’t set for this month.” Provide a “Set up budget” action. Allow eligible expenses to be recorded; show the month as unbudgeted.
- If there are no entries, show “No entries yet” with an Add entry action; don't confuse it with no budget.

The main dashboard status is based on month-to-date bucket use versus the saved monthly bucket limit. No V1 prediction about how much money will be saved by month-end. Do not implement a “Safe to Spend Today” value without a separately approved formula.

## 9. Add entry — quick and safe

Keep the common flow short: choose entry type, amount, bucket/category, date/time, optional payment method/note, Save.

### Entry types

- **Expense:** consumes Essentials or Safety budget; user selects one of those buckets.
- **Safety saving:** amount set aside; bucket is automatically Safety.
- **Investment contribution:** records contribution for SIP, FD, Stocks, Gold SIP or Other investment; bucket automatically Investments.
- **Credit-card payment:** separate action, not a normal expense. It reduces the estimated outstanding and does not consume a budget bucket.

### Categories

Fetch the category allow-list from `GET /api/v1/reference-data/categories` and filter by entry type/bucket. Submit stable `categoryCode`, never display label, as the category identity. Keep labels friendly:

- Essentials: Groceries, Rent, Utilities, Transport, Food & Dining, Medical, Education, Shopping, Subscriptions, Other.
- Safety: Emergency fund/Safety saving, medical/safety expense and approved Other category.
- Investments: SIP, FD, Stocks, Gold SIP, Other investment.
- Credit-card payment: Pay credit-card bill.

Prevent impossible combinations in the form, but if the API rejects one, show a useful error. Don't require a separate user-created category flow in V1.

### Amount/date/time

- Amount is required, greater than ₹0, INR with at most 2 decimal places; reject invalid commas/negative values before submission.
- Date is required and must not be future-dated for an actual event. Use IST. Time can default to current IST local time but remains editable.
- Current month's entries may be created. Past months become read-only; do not show a misleading Save button when the backend will reject the period.
- Note is optional with max 500 characters.
- Use clear payment methods: UPI, Cash, Credit Card, Debit Card, Bank Transfer, Wallet, Other. Require a payment method for every Expense so the app can correctly update tracked credit-card outstanding. For Safety saving and Investment, payment method may be omitted if it is not relevant.
- “Credit Card” as an expense payment method means a purchase made with the card, and increases tracked outstanding. It is not a bill payment.

### Duplicate-submit protection

- Disable Save and show loading state while request is pending.
- Send a unique request UUID/idempotency key for a new entry; retain the same key for a retry of that same submission.
- On network failure, preserve form values and explain whether the result is unknown; reload/query the entry list before blindly creating a replacement.
- On success, clear the form only after confirmed server response and refresh the summary/history.

## 10. Credit-card outstanding UI

V1 shows a combined estimate across all cards, not separate card records.

- On onboarding/settings, optionally ask: “Would you like to track credit-card outstanding?” If Yes, ask: “Current total outstanding across your credit cards (if any).” Default opening amount may be ₹0.00. This sets the opening baseline and tracking-start timestamp. If the user skips setup, show “Credit-card tracking not set up” rather than a misleading ₹0 balance.
- Explain that the number is manually tracked, not synchronized with a bank or card issuer. The opening amount already includes any unpaid purchases before tracking began; don’t add those old purchases to the outstanding a second time. They can still be recorded for expense history.
- Posted expenses paid by Credit Card after tracking starts add to outstanding. If card tracking is not configured, choosing Credit Card as the payment method prompts the user to configure it first. Purchases dated before tracking start may be included in expense history but are already represented by the opening outstanding amount.
- “Record credit-card payment” asks amount, IST date/time, payment method used to pay the bill, and optional note. It is not classified under Essentials/Safety/Investments and is not added to consumption expenditure. A payment dated before tracking began cannot be recorded as a tracked repayment because the opening balance already reflects that point in time.
- Do not allow a payment greater than the currently calculated outstanding. If rejected, explain the available tracked balance.
- If a purchase is voided, recalculate outstanding downward; if a payment is voided, recalculate outstanding upward. Show a confirmation before voiding either event.
- Show a clear label “Estimated credit-card outstanding (manual tracking)” and a brief info hint: “May differ from the issuer’s current statement if purchases/payments have not been recorded here.”
- Per-card limits, statement date, due date, minimum due, and issuer synchronization are out of scope.

## 11. Transactions/history

- Default sort newest first.
- Filters: date range, entry type, bucket, category, status (POSTED/VOID).
- Pagination; do not load lifetime history all at once.
- Each row shows category label, type, amount, bucket/payment detail as relevant, date/time in IST, and status.
- Show VOID entries visually distinct and excluded from active totals.
- Posted entry correction flow is “Void entry” plus create a corrected replacement; no ordinary physical delete or inline edit.
- Confirm void with a modal/dialog that names amount/category/date and warns summaries will update. Optional reason is supported.
- Avoid exposing another user's data; even if user manipulates URL/API, server must enforce ownership.

## 12. Reports

Report views:

- Daily
- Monthly
- Quarterly
- Half-yearly
- Yearly
- Custom date range

Default year basis is the Indian Financial Year (April–March): Q1 Apr–Jun, Q2 Jul–Sep, Q3 Oct–Dec, Q4 Jan–Mar; H1 Apr–Sep, H2 Oct–Mar. Label the fiscal year visibly (e.g. `FY 2026–27`) to avoid confusion with calendar years.

Metrics should keep separate:

- Recorded monthly income (for whole budget months in the selected range only).
- Consumption expenditure.
- Safety savings.
- Investment contributions.
- Estimated current aggregate credit-card outstanding (current balance is a snapshot, not a historical period spend).
- Bucket use and remaining allocation where a budget exists.

Rules:

- Period transactions are based on IST entry date/time.
- Monthly income is a monthly record, not a timestamped receipt. For a partial-month custom range, omit income from the report or state clearly that recorded income is available only for full budget months; do not assign a whole monthly income to one day.
- No-data state says “No records for this period” and allows changing the range.
- Charts have accessible labels/legends and a text/table alternative; they must not imply an investment's current market value.

## 13. Required UI states and error behavior

Every API-backed screen must handle:

1. Initial loading (skeleton or clear loading indicator).
2. Success with data.
3. Empty collection (e.g. no entries yet).
4. Missing budget (`BUDGET_NOT_SET`).
5. Zero income (`NO_INCOME`).
6. Field validation errors.
7. Save pending (disable duplicate submission).
8. Server/network failure with retry guidance and form-state preservation.
9. Session expired/unauthenticated (401 → login).
10. Forbidden or not found (403/404), without exposing private data.
11. Conflict (409), e.g. past month read-only, investments OFF conflicts or credit-card payment exceeds outstanding.
12. Entry already voided / invalid lifecycle transition.
13. No results for current filters.
14. Over-allocation/negative remaining bucket amount.

Use polite, actionable language. Do not show stack traces, database errors, raw JSON, authentication internals or sensitive data. Errors should be associated with the relevant field and also announced to assistive technology.

## 14. Accessibility and responsive behavior

- Use semantic headings, buttons, labels and form controls.
- Every input has an associated visible label; placeholders are not labels.
- Full keyboard operation, visible focus, logical tab order, dialogs that trap/return focus correctly.
- WCAG 2.2 AA contrast target for body text and interactive controls; status uses text/icon in addition to color.
- Touch controls should be comfortably large (aim for 44×44 CSS px where practical).
- Respect browser zoom and narrow viewports; no horizontal scrolling for primary tasks at 320px width.
- Charts include accessible labels/legends and an alternate text summary.
- Respect reduced motion; avoid animation that communicates essential financial status.
- Test with keyboard and at least one screen reader before public release.

## 15. Angular implementation rules

- Use standalone components and strict TypeScript; keep feature code organized (auth, budget, entries, reports, profile/shared UI).
- Centralize API requests in services; components should not directly fetch or duplicate API logic.
- Use Angular `HttpClient` via `provideHttpClient`; configure the default XSRF feature correctly with Spring Security. Do not disable XSRF to “fix” a request error.
- Local dev: prefer an Angular proxy for `/api` to `http://localhost:8080` so browser traffic is same-origin at `localhost:4200`; production must use HTTPS and a deliberate same-origin/reverse-proxy or exact CORS configuration.
- Authentication state may be kept in memory. Do not store passwords/session IDs/tokens in `localStorage`.
- Route guards are UX features only; backend must enforce all authorization.
- Keep one stable API base path `/api/v1`; keep DTO types aligned with backend contracts.
- Amounts in API are decimal strings (e.g. `"30000.00"`). Format them using one shared INR formatting utility. Do not use frontend `number` arithmetic as financial source of truth.
- If local preview math is provided, share test cases and match the backend paise rounding/residual policy exactly; after save, use server-returned values.
- Handle 401/409/429/500 centrally via an HTTP interceptor or shared error service without swallowing field-specific details.
- No API keys or database credentials in Angular environment files. Anything bundled into a browser app is public.

## 16. Test requirements for UI

- Registration validation: required fields, email, +91 mobile, passwords, duplicate-email error.
- Employment/income-type defaults and Other income label behavior.
- Preset selection shows exact percentages; Manual edits mode; total validation.
- Investments OFF asks for Essentials/Safety, sets 0% investments and checks sum=100%; switching OFF is blocked if existing posted investment entry exists.
- Bucket money preview has no ₹0.01 mismatch for edge values; total exactly equals income.
- Add expense/safety saving/investment entry; category list filters correctly.
- Credit-card purchase increases displayed outstanding; repayment reduces it without increasing consumption expenditure; oversize payment rejected.
- Void entry confirmation and summary recalculation.
- `BUDGET_NOT_SET`, `NO_INCOME`, no-entry, no-data, loading, error, 401, 409 and over-allocation states render correctly.
- Date/report filters respect IST, Indian FY boundaries and full-month income semantics.
- Mobile layout and keyboard/accessibility checks for key workflows.
- Smoke test: register/login → set current-month budget → add entry → see updated summary → filter history → logout.

## 17. Definition of done for UI

1. Each screen maps to an approved user story/API contract.
2. Forms have client validation and display backend validation.
3. Loading, empty, success, error, conflict and session-expired states exist.
4. Money data comes from API; Angular does not invent authoritative totals.
5. Mobile and desktop responsive layouts are tested.
6. Accessible labels, keyboard focus and non-color status cues are present.
7. No secrets or tokens are exposed in browser storage or source.
8. Unit tests for validators/calculation preview and component states pass.
9. Full build succeeds (`npm run build`); relevant tests pass (`npm test`/configured Angular test command).
10. Developer report lists changed files, tests, results and known limitations.

## 18. Deferred UI ideas (do not implement in V1)

Custom categories, separate cards/account screens, bills and reminders, recurring entries, goals, family mode, annual salary forecasting, assets/returns, EPF/PPF/NPS, tax, exports/imports, AI assistant, bank synchronization, offline-first sync, and a predictive “Safe to Spend Today” card.

## 19. Technical references

- Angular HTTP setup and XSRF: https://angular.dev/guide/http/setup
- Angular security: https://angular.dev/best-practices/security
- OWASP REST security and CORS: https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html
- OWASP session management: https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
