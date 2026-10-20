# Final Budgeting App Requirements Package

This package has two aligned implementation source-of-truth documents and one draft Flyway SQL migration.

- `SERVICE_REQUIREMENTS_FINAL.md` — Spring Boot/API/database/financial rules/security/tests.
- `UI_REQUIREMENTS_FINAL.md` — Angular screens, workflows, form behavior, states/accessibility and UI tests.
- `V1__create_core_budgeting_tables.sql` — draft MySQL migration for the three business tables.

## Critical notes

- MVP means Minimum Viable Product: the smallest reliable, useful first release.
- Credit-card balance is an estimated combined manual figure, not issuer-synchronized. Card purchases are recorded as expenses; bill payments reduce outstanding and are not a second expense.
- The migration is a draft and has not been run against the user's local MySQL instance. Test on a disposable empty schema first and confirm the server is MySQL 8.0.16+ before relying on enforced CHECK constraints.
- Do not overwrite or drop anything in `budget_db` as part of this package. `V1__...sql` belongs at `src/main/resources/db/migration/` only after review/approval.
