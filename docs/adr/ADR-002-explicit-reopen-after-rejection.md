# ADR-002: Explicit Reopen After Rejection

**Status:** Accepted  
**Date:** 2026-11-24  
**Issue:** [FieldOps-Project/backend#30](https://github.com/FieldOps-Project/backend/issues/30)

## Context

A rejected inspection goes back to the technician with a reason and, optionally,
the items to correct (flow 7.8). The `REJECTED -> IN_PROGRESS` transition can
happen in two ways:

1. **Automatic** — the next synchronization moves the inspection to
   `IN_PROGRESS` as soon as the technician's app pulls the rejection.
2. **Explicit** — the technician calls an action that reopens the inspection.

## Decision

We adopt **option 2 (explicit reopen)**:

```text
POST /api/v1/inspections/{id}/reopen
```

- Only the assigned technician can reopen, and only from `REJECTED`
  (`InspectionAction.REOPEN`). `APPROVED` has no reopen transition (RN-082).
- While the inspection is `REJECTED`, responses are refused with
  `422 INSPECTION_REOPEN_REQUIRED`.
- After the reopen, when the last rejection lists `itemsToCorrect`, only those
  items accept responses (`422 INSPECTION_ITEM_NOT_IN_CORRECTION`); without the
  list, the whole inspection is editable. The rule is enforced by the server.
- The reopen is audited as `INSPECTION_REOPENED`.

## History of corrected responses

Responses are corrected in place. Before an existing response is overwritten,
an `RESPONSE_UPDATED` audit event stores the allow-listed previous and new
values, which keeps the earlier answer recoverable (RN-083). No response history
table is introduced for the MVP.

## Consequences

- The app shows "Requer correcao" from the mobile detail, which carries the latest
  rejection reason and `itemsToCorrect`, and the technician decides when to take
  over the correction.
- Reopening needs a connection, as start and submit already do.
- A new submit followed by a new decision creates a new review cycle; earlier
  cycles stay in `inspection_reviews`.
