# SIP Investment Plans

**Status:** Implemented backend contract  
**Base path:** `/api/v1/sip-plans`  
**Business timezone:** `Asia/Kolkata` (IST)  
**Related migrations:** `V4__add_sip_plans_and_amount_preset_identity.sql`

SIP plans track a user's planned recurring investment and the months in which installments were actually paid. Paying an installment creates a normal `POSTED` `INVESTMENT` money entry. SIP tracking does not fetch NAV, current value, returns, or profit.

## 1. Data model

### `sip_plans`

| Column | Type | Rules |
| --- | --- | --- |
| `sip_plan_id` | `BIGINT UNSIGNED` | Primary key |
| `user_id` | `BIGINT UNSIGNED` | Owner; FK to `users`, `ON DELETE CASCADE` |
| `plan_name` | `VARCHAR(120)` | Trimmed, nonblank |
| `monthly_amount` | `DECIMAL(14,2)` | Greater than `0.00` |
| `installment_day` | `INT` | `1` through `31`; clamped to month length when scheduled |
| `original_start_date` | `DATE` | User's original SIP start |
| `tracking_start_date` | `DATE` | Start of app tracking; not before original start |
| `planned_end_date` | `DATE NULL` | Optional; not before tracking start |
| `actual_ended_date` | `DATE NULL` | Set when stopped/completed; not before tracking start |
| `status` | `VARCHAR(16)` | `ACTIVE`, `STOPPED`, `COMPLETED` |
| `created_at_utc` | `DATETIME(6)` | UTC |
| `updated_at_utc` | `DATETIME(6)` | UTC |

Indexes: `(user_id, status)` and `(user_id, tracking_start_date)`.

### SIP fields on `money_entries`

| Column | Type | Rules |
| --- | --- | --- |
| `sip_plan_id` | `BIGINT UNSIGNED NULL` | FK to `sip_plans`, `ON DELETE RESTRICT` |
| `sip_installment_period` | `DATE NULL` | First day of installment month |
| `active_sip_installment_key` | generated `VARCHAR(64)` | `sip_plan_id:sip_installment_period` only when POSTED; unique |

A SIP-linked entry must be `entry_type=INVESTMENT`, `bucket=INVESTMENTS`, `category_code=SIP`, and its installment period must be day `1`. Both SIP fields must be set together or both must be null.

## 2. Schedule rules

- A plan's first installment is scheduled in the `trackingStartDate` month on `min(installmentDay, lengthOfMonth)`. If that date falls before `trackingStartDate`, the first installment moves to the next month.
- Each later month uses `min(installmentDay, lengthOfMonth)`, so day `31` becomes the last day in shorter months.
- The last scheduled installment is on or before `plannedEndDate` and `actualEndedDate` when either is present.
- A plan is effectively `COMPLETED` when its stored status is `COMPLETED` or the current IST date is after `plannedEndDate`. It is effectively `STOPPED` only when stored as `STOPPED`; otherwise it is `ACTIVE`.
- An installment is `PAID` when a POSTED SIP-linked entry exists for that plan-month. Otherwise it is `OVERDUE` before today, `DUE` today, or `UPCOMING` after today.

## 3. Endpoints

All endpoints require an authenticated session and enforce ownership from the authenticated principal. Client-supplied user IDs are ignored.

### Create a plan

`POST /api/v1/sip-plans` -> `201 Created`

```json
{
  "planName": "Index fund SIP",
  "monthlyAmount": "5000.00",
  "installmentDay": 5,
  "originalStartDate": "2025-04-05",
  "trackingStartDate": "2026-10-01",
  "plannedEndDate": "2030-03-05"
}
```

`planName`, `monthlyAmount`, `installmentDay`, `originalStartDate`, and `trackingStartDate` are required. `plannedEndDate` is optional. The backend trims `planName`, normalizes the amount to two decimals, and creates the plan as `ACTIVE`.

Response:

```json
{
  "sipPlanId": 1,
  "investmentType": "SIP",
  "planName": "Index fund SIP",
  "monthlyAmount": "5000.00",
  "installmentDay": 5,
  "originalStartDate": "2025-04-05",
  "trackingStartDate": "2026-10-01",
  "plannedEndDate": "2030-03-05",
  "actualEndedDate": null,
  "status": "ACTIVE",
  "createdAtUtc": "2026-10-31T09:20:00Z",
  "updatedAtUtc": "2026-10-31T09:20:00Z"
}
```

### List plans

`GET /api/v1/sip-plans` -> `200 OK`

```json
{
  "content": [
    {
      "sipPlanId": 1,
      "investmentType": "SIP",
      "planName": "Index fund SIP",
      "monthlyAmount": "5000.00",
      "installmentDay": 5,
      "originalStartDate": "2025-04-05",
      "trackingStartDate": "2026-10-01",
      "plannedEndDate": "2030-03-05",
      "actualEndedDate": null,
      "status": "ACTIVE",
      "createdAtUtc": "2026-10-31T09:20:00Z",
      "updatedAtUtc": "2026-10-31T09:20:00Z"
    }
  ],
  "activeMonthlyCommitment": "5000.00"
}
```

`activeMonthlyCommitment` sums `monthlyAmount` for plans whose effective status is `ACTIVE` on the current IST date.

### Get one plan

`GET /api/v1/sip-plans/{id}` -> `200 OK` with the same plan object. Unknown or another user's plan returns `404 SIP_PLAN_NOT_FOUND`.

### Update a plan

`PUT /api/v1/sip-plans/{id}` -> `200 OK`

Body is the same shape as create and replaces the editable fields. Stopped plans return `409 SIP_PLAN_NOT_ACTIVE`; completed plans return `409 SIP_PLAN_COMPLETED`.

Changing `originalStartDate` or `trackingStartDate` after any POSTED installment exists returns `409 SIP_PLAN_DATES_LOCKED`. Name, amount, installment day, and planned end date remain editable while the plan is active.

### Stop a plan

`POST /api/v1/sip-plans/{id}/stop` -> `200 OK`

```json
{
  "stopDate": "2026-10-31"
}
```

The body is optional; `stopDate` defaults to today in IST. It must not be in the future, before `trackingStartDate`, or after `plannedEndDate`. Stopping sets `status=STOPPED` and `actualEndedDate=stopDate`.

Errors: `409 SIP_PLAN_ALREADY_STOPPED`, `409 SIP_PLAN_COMPLETED`, `400 SIP_STOP_DATE_FUTURE`, `400 SIP_STOP_DATE_BEFORE_TRACKING`, `400 SIP_STOP_DATE_INVALID`.

### List installments

`GET /api/v1/sip-plans/installments?fromDate=2026-10-01&toDate=2026-12-31` -> `200 OK`  
`GET /api/v1/sip-plans/{id}/installments?fromDate=2026-10-01&toDate=2026-12-31` -> `200 OK`

If both dates are omitted, the range defaults to the current IST month. If only one is supplied, the request fails with `400 DATE_RANGE_INCOMPLETE`. `toDate` before `fromDate` returns `400 DATE_RANGE_INVALID`. A range spanning 60 months or more returns `400 DATE_RANGE_TOO_LARGE`.

```json
{
  "fromDate": "2026-10-01",
  "toDate": "2026-12-31",
  "content": [
    {
      "sipPlanId": 1,
      "planName": "Index fund SIP",
      "period": "2026-10",
      "dueDate": "2026-10-05",
      "amount": "5000.00",
      "status": "PAID",
      "paidMoneyEntryId": 42,
      "paidAtUtc": "2026-10-05T04:30:00Z",
      "paidDate": "2026-10-05"
    },
    {
      "sipPlanId": 1,
      "planName": "Index fund SIP",
      "period": "2026-11",
      "dueDate": "2026-11-05",
      "amount": "5000.00",
      "status": "UPCOMING",
      "paidMoneyEntryId": null,
      "paidAtUtc": null,
      "paidDate": null
    }
  ]
}
```

Content is sorted by `dueDate`, then `sipPlanId`.

### Mark an installment paid

`POST /api/v1/sip-plans/{id}/installments/{yyyy-MM}/pay` -> `201 Created` for a new payment, `200 OK` for an idempotent replay

```json
{
  "paymentDate": "2026-10-05",
  "paymentTime": "10:00",
  "note": "October installment"
}
```

The body is optional. `paymentDate` defaults to today and `paymentTime` defaults to the current IST time. `note` is optional, maximum 500 characters.

Payment behavior:

1. Parses `period` as `YYYY-MM`; invalid text returns `400 INSTALLMENT_PERIOD_INVALID`.
2. Rejects a payment month before the current IST month with `409 PERIOD_READ_ONLY` (via `PeriodReadOnlyException`).
3. Rejects a payment date/time in the future with `400 ENTRY_DATE_FUTURE`.
4. Returns the existing POSTED entry with `idempotentReplay=true` and HTTP `200` when the plan-month is already paid.
5. Rejects stopped/completed plans with `409 SIP_PLAN_NOT_ACTIVE` or `409 SIP_PLAN_COMPLETED`.
6. Rejects a period outside the plan schedule with `409 SIP_INSTALLMENT_NOT_SCHEDULED`.
7. Requires the budget for the payment month to exist (`404 BUDGET_NOT_SET`) and have Investments enabled (`409 INVESTMENTS_DISABLED`).
8. Creates a POSTED `INVESTMENT` entry in `INVESTMENTS` with category `SIP`, amount equal to the plan's `monthlyAmount`, and the plan/period links.
9. If a concurrent duplicate insert wins the unique constraint, the backend reloads and returns the existing entry as a replay.

Response:

```json
{
  "installment": {
    "sipPlanId": 1,
    "planName": "Index fund SIP",
    "period": "2026-10",
    "dueDate": "2026-10-05",
    "amount": "5000.00",
    "status": "PAID",
    "paidMoneyEntryId": 42,
    "paidAtUtc": "2026-10-05T04:30:00Z",
    "paidDate": "2026-10-05"
  },
  "moneyEntry": {
    "moneyEntryId": 42,
    "entryAtUtc": "2026-10-05T04:30:00Z",
    "entryType": "INVESTMENT",
    "bucket": "INVESTMENTS",
    "categoryCode": "SIP",
    "amount": "5000.00",
    "paymentMethod": null,
    "note": "October installment",
    "status": "POSTED",
    "clientRequestId": null,
    "sipPlanId": 1,
    "sipInstallmentPeriod": "2026-10-01",
    "voidedAtUtc": null,
    "voidReason": null,
    "createdAtUtc": "2026-10-05T04:30:01Z",
    "updatedAtUtc": "2026-10-05T04:30:01Z"
  },
  "idempotentReplay": false
}
```

The payment month is based on `paymentDate`, not the installment period. A past installment can be paid in the current month, but the budget check applies to the current payment month. The generic void endpoint may void the linked money entry; doing so makes the installment unpaid and allows a later payment for the same plan-month.

### Commitment summary

`GET /api/v1/sip-plans/summary?month=2026-10` -> `200 OK`

`month` defaults to the current IST month when omitted. Invalid text returns `400 MONTH_INVALID`.

```json
{
  "month": "2026-10",
  "investmentsAmount": "15000.00",
  "activeMonthlyCommitment": "10000.00",
  "withinAllocation": true,
  "overAllocationAmount": "0.00",
  "activePlanCount": 2,
  "message": "Active SIP commitments are within this month's Investments allocation."
}
```

The commitment includes only effectively ACTIVE plans that have at least one scheduled due date in the requested month. If the month has no budget, `investmentsAmount` and `withinAllocation` are null and the message explains that no comparison is available. If commitment exceeds the Investments allocation, `withinAllocation=false` and `overAllocationAmount` is the positive difference.

## 4. Error codes

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | `REQUEST_INVALID` | Missing body or required plan fields |
| 400 | `PLAN_NAME_INVALID` | Blank or over 120 characters |
| 400 | `MONTHLY_AMOUNT_INVALID` | Not greater than zero or above maximum |
| 400 | `MONTHLY_AMOUNT_PRECISION` | More than two decimal places |
| 400 | `INSTALLMENT_DAY_INVALID` | Outside 1–31 |
| 400 | `TRACKING_START_BEFORE_ORIGINAL` | Tracking start precedes original start |
| 400 | `PLANNED_END_BEFORE_TRACKING` | Planned end precedes tracking start |
| 400 | `SIP_STOP_DATE_FUTURE` | Stop date is in the future |
| 400 | `SIP_STOP_DATE_BEFORE_TRACKING` | Stop date precedes tracking start |
| 400 | `SIP_STOP_DATE_INVALID` | Stop date is after planned end |
| 400 | `NOTE_TOO_LONG` | Note exceeds 500 characters |
| 400 | `INSTALLMENT_PERIOD_INVALID` | Period is not `YYYY-MM` |
| 400 | `ENTRY_DATE_FUTURE` | Payment date/time is in the future |
| 400 | `MONTH_INVALID` | Summary month is not `YYYY-MM` |
| 400 | `DATE_RANGE_INCOMPLETE` | Only one installment range date supplied |
| 400 | `DATE_RANGE_INVALID` | End date precedes start date |
| 400 | `DATE_RANGE_TOO_LARGE` | Range spans 60 months or more |
| 404 | `SIP_PLAN_NOT_FOUND` | Plan missing or not owned by caller |
| 404 | `BUDGET_NOT_SET` | Payment month has no budget |
| 409 | `SIP_PLAN_NOT_ACTIVE` | Plan is stopped |
| 409 | `SIP_PLAN_COMPLETED` | Plan is completed |
| 409 | `SIP_PLAN_ALREADY_STOPPED` | Stop requested twice |
| 409 | `SIP_PLAN_DATES_LOCKED` | Start dates changed after a paid installment |
| 409 | `SIP_INSTALLMENT_NOT_SCHEDULED` | Period is not part of the schedule |
| 409 | `INVESTMENTS_DISABLED` | Payment month has Investments disabled |
| 409 | `PERIOD_READ_ONLY` | Payment month is in the past |
| 500 | `SIP_INSTALLMENT_SAVE_FAILED` | Duplicate recovery could not find the entry |

All errors use the standard error payload with `timestamp`, `status`, `code`, `message`, `fieldErrors`, and `traceId`.

## 5. Frontend integration notes

- Use the category catalog for investment options: `SIP`, `MUTUAL_FUND_LUMPSUM`, `STOCKS`, `FD`, `RD`, `PPF`, `GOLD`, `OTHER_INVESTMENT`. `GOLD_SIP` remains supported for legacy data but must not appear as a duplicate new option.
- Send and display amounts as decimal strings with two fractional digits.
- Treat `paymentMethod` as null for SIP-generated entries; do not require a payment method on this flow.
- Use the returned `status`, `dueDate`, and `paidDate` fields rather than recomputing schedule/status in Angular.
- Show the `message` from the commitment summary when `withinAllocation` is null.
- On `idempotentReplay=true`, treat the response as success and refresh the installment list instead of showing a duplicate error.
- After a successful payment, refresh the installment list and the month's investment summary. Do not create a second manual investment entry for the same installment.
