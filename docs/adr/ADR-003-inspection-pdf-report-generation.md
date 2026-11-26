# ADR-003: Inspection PDF report generation

## Status

Accepted

## Context

The inspection report must reproduce the checklist snapshot, answers, evidence,
non-conformities and review decision. Evidence files may be much larger than the
PDF needs to display.

## Decision

The first release generates the PDF synchronously for a single inspection. The
endpoint converts evidence images to bounded JPEG thumbnails before embedding
them, and keeps a missing private object as report metadata instead of exposing
the storage error or sensitive data.

This is suitable for the current small inspection workflow. A later release
should move generation to an asynchronous job with a download resource when
inspection size or evidence volume makes the synchronous request approach its
request timeout.

## Consequences

- The response is immediately downloadable and does not need job state yet.
- The report has a bounded image dimension and avoids embedding original files.
- The export is auditable without storing report contents in the audit event.
- The synchronous limit must be revisited before supporting inspections with
  dozens of large evidence files.
