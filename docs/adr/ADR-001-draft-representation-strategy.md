# ADR-001: Draft Representation Strategy for Inspection Templates

**Status:** Accepted  
**Date:** 2026-09-28  
**Issue:** [FieldOps-Project/backend#12](https://github.com/FieldOps-Project/backend/issues/12)

## Context

The inspection template model requires a draft editing stage before a version
is formally published and made available for scheduling inspections. Two
approaches were considered:

1. **Parallel tables** — separate `draft_sections` / `draft_items` tables that
   mirror the published structure. On publish, rows are copied into the
   versioned tables.

2. **Draft-as-version-zero** — reuse the same `inspection_template_versions`,
   `template_sections`, and `template_items` tables. A draft is simply a
   version row with `version_number = 0` and `published_at IS NULL`.

## Decision

We adopt **option 2 (draft-as-version-zero)**.

A draft version is represented by a row in `inspection_template_versions` where:

- `version_number = 0`
- `published_at IS NULL`
- `published_by IS NULL`
- `active_for_new_inspections = FALSE`

Sections and items belonging to the draft use the exact same schema as
published versions (`template_sections`, `template_items`).

When the draft is published, a new row is inserted with
`version_number = <next>`, `published_at = now()`, and the draft row
(`version_number = 0`) is either deleted or replaced with a fresh empty draft
for further editing.

## Consequences

### Positive

- **Single code path** — repositories, mappers, and validations work
  identically for drafts and published versions.
- **No data migration on publish** — sections and items already exist in the
  correct tables; only the version metadata changes.
- **Simpler schema** — avoids duplicating four tables into eight.

### Negative

- **Unique constraint awareness** — queries and application logic must account
  for `version_number = 0` being a reserved sentinel. The `UNIQUE(template_id,
  version_number)` index naturally prevents duplicates.
- **Draft cleanup** — if a template is deleted, the cascade must also remove
  the draft version and its children (handled by `ON DELETE CASCADE` on FKs).

## References

- [10 - Modelo de Dados §10.7](https://github.com/FieldOps-Project/docs/blob/main/notion/10-modelo-de-dados.md)
- Issue #12 specification: "Modelar o rascunho como uma versão de trabalho
  (version_number nulo ou zero, published_at nulo) mantém uma única estrutura
  em vez de duas paralelas — registrar a escolha adotada em ADR."
