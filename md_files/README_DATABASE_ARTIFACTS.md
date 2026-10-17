# Budgeting App — Database Design Package

**Project:** Monthly Budgeting Money Planner / Indian Family Money Planner  
**Technology target:** MySQL 8.x (InnoDB), Spring Boot, JPA where appropriate, Flyway  
**Status:** Design draft 0.1 — ready for stakeholder review; **not yet approved for DDL execution**  
**Date:** 2026-10-17

## Purpose

This package translates the current product requirements and architecture documents into a reviewable data design before any tables are created in `budget_db`.

## Package contents

| File | Purpose |
|---|---|
| `DATABASE_DESIGN.md` | Table inventory, columns, data types, keys, constraints and indexing approach |
| `DATABASE_ERD.md` | Mermaid relationship diagrams grouped by domain |
| `REQUIREMENTS_TO_TABLE_MATRIX.md` | Traceability from product capabilities to tables and validation requirements |
| `CATALOG_SPECIFICATION.md` | Rules for `t_catalog` and `t_catalog_code`, plus catalog-vs-user-data classification |
| `FINANCIAL_BUSINESS_RULES.md` | Transaction, income, planning, allocation, transfer, credit-card, goal and safe-to-spend rules |
| `FLYWAY_PLAN.md` | Migration order, seed strategy, verification and rollback approach |
| `OPEN_DECISIONS.md` | Product decisions that must be approved before production-ready DDL is generated |

## What this package does not do

- It does not connect to MySQL or modify `budget_db`.
- It does not create runnable Flyway migration files yet.
- It does not claim every future requirement can be predicted with certainty.
- It does not include credentials, API keys or user financial data.

## Source basis

The main sources used are the existing project documents:

- `PROJECT_DETAILED_FEATURE_REQUIREMENTS.md`
- `CODE_APP_ARCHITECTURE_FLOW.md`
- `PHASE_WISE_PROMPTS.md`
- `servicePrompt.md`
- `UIPrompt.md`

Where these sources leave a business rule unspecified or contain a conflict (notably Safe-to-Spend formula and crypto-off allocation behaviour), this package explicitly flags it rather than silently inventing a rule.

## Review gate

Do not generate/apply DDL until:

1. Table scope and field definitions have been reviewed.
2. Items in `OPEN_DECISIONS.md` have an agreed resolution or are explicitly deferred with a safe default.
3. The core transaction and credit-card scenarios in `FINANCIAL_BUSINESS_RULES.md` pass review.
4. ERD, table dictionary and migration plan agree with one another.

## Design stance

Future-ready means preserving clear ownership boundaries, normalized relationships, stable identifiers, financial correctness and controlled schema evolution. It does **not** mean building all V2/V3 modules or placing every option in a single generic catalog table on day one.
