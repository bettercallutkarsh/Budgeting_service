# Reference Catalog Specification

## 1. Purpose

`t_catalog` and `t_catalog_code` provide a controlled source for application-wide or configurable list values. They are not a universal container for all user data or every business state.

## 2. Proposed structure

### `t_catalog`

- `catalog_id` — primary key
- `catalog_code` — immutable stable machine code, unique
- `catalog_name` — human-readable name
- `description` — optional meaning
- `is_active`, `created_at`, `updated_at`

### `t_catalog_code`

- `catalog_code_id` — primary key
- `catalog_id` — FK to `t_catalog`
- `value_code` — stable machine code, unique within catalog
- `value_label` — display label
- `description`, `display_order`
- `is_default`, `is_active`, `created_at`, `updated_at`

Constraints: `UNIQUE(catalog_code)` on `t_catalog`; `UNIQUE(catalog_id, value_code)` on `t_catalog_code`. Add keys needed for joins. A code must not be silently renamed after persisted data uses it; add a new code and migrate explicitly if semantics change.

## 3. Catalog-driven values vs other data

| Type | Examples | How to model |
|---|---|---|
| Shared selectable reference values that may be configured | Payment methods, recurrence frequency, income confidence/classification, fixed/variable/flexible behavior | Catalog, if the product genuinely needs administrative activation/order/labels |
| System-wide display choices | `ACCOUNT_TYPE` display labels, other stable reference labels | Can appear in catalog for display, but backend validates the code against the required catalog and domain semantics |
| User/household-owned entities | Categories, accounts, goals, payees, bill templates, income sources | Dedicated relational tables; do not make each user record a catalog code |
| Business lifecycle states | `POSTED`, `VOID`, `ACTIVE`, `CANCELLED`, permission roles | Prefer code-defined domain constants/enums and backend transition rules; catalogs should not let an administrator change core behaviour casually |
| Calculated values | Current balance, budget remaining, safe-to-spend, net worth | Derived by deterministic backend logic; never a dropdown/catalog value |
| Allocation hierarchy/template percentages | Conservative/Balanced/Aggressive; Equity/Debt/Commodity; goal tags | Dedicated allocation template/item tables because values have hierarchy, percentages, versioning and validation rules |
| Currency ISO codes | INR, USD, etc. | Store as `CHAR(3)` and validate against a known currency registry/allowlist; no need to make currencies user-managed catalog values in V1 |

## 4. Suggested initial catalogs and values

These are proposed seed values; final inclusion should follow the V1 form/API scope.

| `catalog_code` | Suggested `value_code` values | Use |
|---|---|---|
| `ACCOUNT_TYPE` | `CASH`, `BANK`, `WALLET`, `CREDIT_CARD` | Account dropdown; card subtype validation is still required |
| `PAYMENT_METHOD` | `UPI`, `CASH`, `CREDIT_CARD`, `DEBIT_CARD`, `BANK_TRANSFER`, `WALLET`, `OTHER` | User-selected payment rail/method, separate from account |
| `INCOME_CLASSIFICATION` | `GUARANTEED`, `EXPECTED`, `VARIABLE`, `ONE_TIME` | Confidence/type of a planned income occurrence |
| `RECURRENCE_FREQUENCY` | `DAILY`, `WEEKLY`, `MONTHLY`, `QUARTERLY`, `HALF_YEARLY`, `YEARLY` | Schedules for recurring rules |
| `SPENDING_BEHAVIOR` | `FIXED`, `VARIABLE`, `FLEXIBLE` | Default category classification; split can override |

Possible later catalogs: notification preference categories, localization labels or administrator-managed reporting groups. Avoid duplicate catalogs with identical meaning (for example, use one recurrence-frequency catalog unless the semantics truly differ).

## 5. Safe catalog consumption pattern

1. UI fetches only active values for the requested catalog.
2. UI submits the selected stable value ID/code; it is never the authority.
3. Service confirms that the referenced value belongs to the expected catalog, is active and is allowed for the requested business action.
4. Database FK confirms the value row exists.
5. If an existing record references a value, mark it inactive instead of deleting it.
6. Core state transitions remain code-defined and guarded by services; do not permit catalog edits to redefine financial behaviour.
7. Cache reference data if needed, with cache invalidation/version handling. Start simple; avoid prematurely distributed cache.

## 6. Household-specific categories

Seed starter Indian categories at household creation (for household, transport, family, financial spending and income). Those rows are saved into `categories` with that `household_id`; `is_system_default=true` indicates they began as a seeded default. Users can rename/deactivate within defined constraints. Avoid global category rows with a nullable `household_id` in the transactional category table unless a reviewed inheritance model is designed.

## 7. Open catalog decisions

- Whether administrators can add/deactivate catalog values via UI or changes are shipped only through migrations.
- Whether labels need English/Hindi localization in V1 or later.
- Whether `ACCOUNT_TYPE` and payment method choices require catalog-level editability, or fixed core enums with catalog-provided labels are sufficient.
- Cross-catalog validation is a service responsibility unless a more specialized reference schema is chosen; generic catalog FK alone cannot prove a value belongs to the correct catalog.
