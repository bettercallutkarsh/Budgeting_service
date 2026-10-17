# Flyway Migration Plan

**Status:** sequencing plan only; no migration SQL has been generated or applied.  
**Target:** Spring Boot + MySQL 8.x; Flyway versioned SQL migrations.  
**Database:** `budget_db`.

## 1. Migration policy

- Schema changes go through Flyway. Do not manually create tables in DBeaver as the primary workflow.
- Do not use Hibernate `ddl-auto=create` or `create-drop` in persistent environments. Use validation/none after migration policy is in place.
- Versioned migrations are immutable after they have been applied to a shared environment. Add a new migration to alter schema; do not edit an already-applied migration to repair it.
- Keep DDL separate from large/reference seed data where practical; seed reference codes with predictable stable IDs or lookup by stable code. Do not depend on auto-increment values being the same across environments.
- For risky migrations: test on a disposable local schema, back up any data, verify migration checksum/status, then apply.
- Store database credentials in environment variables or a secret manager, never in Git.

## 2. Prerequisite gate before the first migration

Do not apply migrations until these are approved:

1. Items in `OPEN_DECISIONS.md` that affect schema or data semantics.
2. Final column/constraint definitions in `DATABASE_DESIGN.md`.
3. ERD and Requirements Matrix updated to match the dictionary.
4. MySQL version confirmed (target MySQL 8.x/InnoDB); collation and timezone conventions documented.
5. Existing `budget_db` inspected for current tables/data and a local backup/export available. Do not drop/reset the schema casually.
6. Spring Boot backend initialized with Flyway dependency and datasource settings; current backend directory was reported to lack `pom.xml`/Gradle files and Java source, so it is not yet ready to apply migrations from the application.

## 3. Proposed versioned migration sequence

Recommended file names (change names only before any shared migration is applied):

| Version | File | Contents/dependencies |
|---|---|---|
| `V001` | `V001__create_catalog_tables.sql` | `t_catalog`, `t_catalog_code`, unique keys and stable reference catalog/value seeds. No user-specific data. |
| `V002` | `V002__create_users_and_credentials.sql` | `users`, `user_credentials`, `user_preferences`; email normalization policy/index. |
| `V003` | `V003__create_households_and_memberships.sql` | `households`, `household_members`, optionally `household_invitations`; tenant keys and role/status constraints. |
| `V004` | `V004__create_accounts_categories_payees_income_sources.sql` | `accounts`, `credit_card_details` can be in a later migration if desired; `categories`, `payees`, `income_sources`; same-household keys. |
| `V005` | `V005__create_transactions_and_splits.sql` | `transactions`, `transaction_splits`; types/status checks, required FKs/indexes. |
| `V006` | `V006__create_account_transfers.sql` | `account_transfers`; transfer constraints, same-household endpoints, fee linkage. |
| `V007` | `V007__create_financial_plans_and_income_entries.sql` | `financial_year_plans`, `monthly_plans`, `planned_income_entries`; plan-version/period checks and actual receipt link. To avoid a forward FK dependency, create `monthly_plans` first without the optional `selected_allocation_template_id` FK. |
| `V008` | `V008__create_allocation_templates_and_plan_allocations.sql` | Create `allocation_templates`, `allocation_template_items`, and `monthly_plan_allocations`; then add `monthly_plans.selected_allocation_template_id` and its FK. Seed presets only after crypto-off decision. |
| `V009` | `V009__create_monthly_budgets.sql` | `budgets` and category/month uniqueness/indexes. |
| `V010` | `V010__create_recurring_rules_and_occurrences.sql` | `recurring_rules`, `recurring_occurrences`; idempotency unique key. |
| `V011` | `V011__create_bills_and_bill_payments.sql` | `bill_templates`, `bill_occurrences`, `bill_payments`; partial-payment relationships. |
| `V012` | `V012__create_credit_card_statements.sql` | `credit_card_statements`; require `credit_card_details` if still separate. |
| `V013` | `V013__create_goals_and_contributions.sql` | `goals`, `goal_contributions`; only after contribution semantics are approved. |
| `V014` | `V014__create_notifications_and_audit_logs.sql` | `notifications`, `audit_logs`; access and retention policy. |
| `V015` | `V015__seed_household_category_template.sql` | Only if seed strategy is database-stored; otherwise category defaults are copied into each new household by application service. |

If `credit_card_details` is included in V004, V012 only creates statements. If invitations or optional features are deferred, omit them from the initial migration sequence and record the scope decision.

## 4. Reference seed strategy

- Seed catalog definitions and values by stable `catalog_code` + `value_code`, not by assuming auto-increment IDs.
- Each seed migration must be safe to run once via Flyway. Avoid an improvised manual rerun that causes duplicates.
- Do not seed final allocation percentages until the crypto-off allocation decision is approved.
- System categories should be copied into each household at household creation with `is_system_default=true`; never share editable household categories as a single global mutable row.
- A seed verification test should assert required catalog codes/value codes are present and active.

## 5. Validation after each migration

After applying migrations to a clean development database:

1. `flyway_schema_history` shows the expected migration versions and success status.
2. All expected tables exist in `budget_db`.
3. PK, FK, unique constraints, CHECK constraints and indexes are present.
4. No FK connects records across households in test fixtures.
5. Seed values resolve by stable code, not hardcoded IDs.
6. Flyway clean install on an empty throwaway database reproduces the same schema.
7. Schema validation against JPA mappings passes after backend project is initialized.
8. Automated integration tests cover the critical scenarios in `FINANCIAL_BUSINESS_RULES.md`.

## 6. Rollback/recovery

- Flyway versioned SQL migrations are not automatically rolled back by editing the file. For development, restore a disposable schema or write a new forward-fix migration after review.
- Before a shared/staging/production migration, take and test an appropriate backup/restore point.
- Never use `flyway clean` against the intended local database without explicit confirmation; it can drop objects.
- The schema plan does not authorize destructive changes to existing `budget_db` data.

## 7. Definition of ready for first DDL

- [ ] Open decisions are resolved or explicitly deferred with safe scope.
- [ ] All V1 tables/columns approved.
- [ ] ERD matches data dictionary.
- [ ] Constraints and indexes reviewed for tenant isolation.
- [ ] Financial invariant tests specified.
- [ ] Spring Boot/Flyway project exists and datasource is configured.
- [ ] Empty-schema migration test passes before use with any existing data.
