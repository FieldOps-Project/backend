# ADR-004: Push notification after inspection assignment

## Context

Technicians learn about new assignments through offline synchronization. The backend must register Expo push devices and notify the assigned technician without making the assignment depend on an external service.

## Decision

Device registrations are stored by authenticated technician and device identifier. Assignment use cases publish an `InspectionAssignedEvent` inside their transaction. A transactional listener consumes the event after commit and asynchronously sends a generic Expo notification whose data contains only the inspection identifier.

Expo's `DeviceNotRegistered` response removes the affected device. Other delivery failures are logged without tokens or notification content and do not affect the assignment transaction.

## Consequences

- A successful assignment is durable even when Expo is unavailable.
- Notifications are best effort and may be duplicated or arrive out of order; synchronization remains the source of truth.
- The push executor is bounded, so delivery cannot create an unbounded number of threads.
