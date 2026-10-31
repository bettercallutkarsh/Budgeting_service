# Final Budgeting App Requirements Package

This package contains the aligned implementation source-of-truth documents and the Flyway migrations.

- `SERVICE_REQUIREMENTS_FINAL.md` - Spring Boot/API/database/financial rules/security/tests.
- `UI_REQUIREMENTS_FINAL.md` - Angular screens, workflows, form behavior, states/accessibility and UI tests.
- `SIP_PLANS.md` - SIP schema, scheduling rules, API contract, error codes and frontend integration notes.
- `LOGGING_AND_OBSERVABILITY.md` - request correlation, log events and sensitive-data policy.
- `V1__create_core_budgeting_tables.sql` - reference copy of the core-table migration.

The authoritative migrations (`V1` through `V4`) live under `src/main/resources/db/migration/`.

## Critical notes

- MVP means Minimum Viable Product: the smallest reliable, useful first release.
- Credit-card balance is an estimated combined manual figure, not issuer-synchronized. Card purchases are recorded as expenses; bill payments reduce outstanding and are not a second expense.
- Test migrations on a disposable schema first and confirm the server is MySQL 8.0.16+ before relying on enforced CHECK constraints.
- Do not overwrite or drop anything in `budget_db` as part of this package.
- Once a migration has been applied or shared, do not rewrite it; add a new forward migration.
