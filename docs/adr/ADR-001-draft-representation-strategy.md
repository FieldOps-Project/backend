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

### Publication: copy the draft, keep the draft

*Amended on 2026-10-23 for [FieldOps-Project/backend#14](https://github.com/FieldOps-Project/backend/issues/14).*

When the draft is published, its sections and items are **deep-copied** into a
new version row with `version_number = max(version_number) + 1`,
`published_at = now()`, `published_by = <actor>` and
`active_for_new_inspections = TRUE`. The draft row (`version_number = 0`) is
kept untouched, so the next version starts from the content just published
instead of from an empty draft.

- The template row is locked (`SELECT ... FOR UPDATE`) while the next number is
  computed, so concurrent publications get consecutive numbers; the
  `UNIQUE(template_id, version_number)` constraint is the last line of defence.
- Published rows are immutable in the application: every draft mutation calls
  `InspectionTemplateVersion.ensureEditable()`, which rejects published
  versions with `409 TEMPLATE_VERSION_PUBLISHED`. There is no database trigger.
- The only field that changes after publication is
  `active_for_new_inspections`, which retires a version for new scheduling
  without affecting inspections that already use it (RN-022).
- Publication validates the whole draft first and reports every pending issue
  at once (`422 TEMPLATE_PUBLISH_INVALID` with `fieldErrors`).

## Consequences

### Positive

- **Single code path** — repositories, mappers, and validations work
  identically for drafts and published versions.
- **Publish is a plain copy** — sections and items are copied between rows of
  the same tables; no structural mapping between draft and published schemas.
- **Continuous editing** — the draft survives publication, so the builder can
  keep evolving the template while earlier versions remain frozen.
- **Simpler schema** — avoids duplicating four tables into eight.

### Negative

- **Unique constraint awareness** — queries and application logic must account
  for `version_number = 0` being a reserved sentinel. The `UNIQUE(template_id,
  version_number)` index naturally prevents duplicates.
- **Storage grows per version** — each publication duplicates the full
  structure. Templates are small, so this is acceptable.
- **Draft cleanup** — if a template is deleted, the cascade must also remove
  the draft version and its children (handled by `ON DELETE CASCADE` on FKs).

## References

- [10 - Modelo de Dados §10.7](https://github.com/FieldOps-Project/docs/blob/main/notion/10-modelo-de-dados.md)
- Issue #12 specification: "Modelar o rascunho como uma versão de trabalho
  (version_number nulo ou zero, published_at nulo) mantém uma única estrutura
  em vez de duas paralelas — registrar a escolha adotada em ADR."
