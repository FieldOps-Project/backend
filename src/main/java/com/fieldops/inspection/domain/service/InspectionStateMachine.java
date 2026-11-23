package com.fieldops.inspection.domain.service;

import com.fieldops.inspection.domain.model.InspectionAction;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.shared.domain.exception.ConflictException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Single source of truth for the inspection state transitions from document 7.6.
 */
@Component
public class InspectionStateMachine {

    private static final Map<Transition, InspectionStatus> TRANSITIONS = Map.ofEntries(
            Map.entry(new Transition(InspectionStatus.DRAFT, InspectionAction.ASSIGN), InspectionStatus.ASSIGNED),
            Map.entry(new Transition(InspectionStatus.ASSIGNED, InspectionAction.START), InspectionStatus.IN_PROGRESS),
            Map.entry(new Transition(InspectionStatus.ASSIGNED, InspectionAction.CANCEL), InspectionStatus.CANCELED),
            Map.entry(new Transition(InspectionStatus.IN_PROGRESS, InspectionAction.SUBMIT), InspectionStatus.SUBMITTED),
            Map.entry(new Transition(InspectionStatus.IN_PROGRESS, InspectionAction.CANCEL), InspectionStatus.CANCELED),
            Map.entry(new Transition(InspectionStatus.SUBMITTED, InspectionAction.START_REVIEW), InspectionStatus.UNDER_REVIEW),
            Map.entry(new Transition(InspectionStatus.UNDER_REVIEW, InspectionAction.APPROVE), InspectionStatus.APPROVED),
            Map.entry(new Transition(InspectionStatus.UNDER_REVIEW, InspectionAction.REJECT), InspectionStatus.REJECTED),
            Map.entry(new Transition(InspectionStatus.REJECTED, InspectionAction.REOPEN), InspectionStatus.IN_PROGRESS),
            Map.entry(new Transition(InspectionStatus.REJECTED, InspectionAction.CANCEL), InspectionStatus.CANCELED)
    );

    public InspectionStatus assertTransitionValid(InspectionStatus current, InspectionAction action) {
        Objects.requireNonNull(current, "current status must not be null");
        Objects.requireNonNull(action, "action must not be null");

        InspectionStatus next = TRANSITIONS.get(new Transition(current, action));
        if (next == null) {
            throw new ConflictException(
                    "INSPECTION_INVALID_TRANSITION",
                    "Cannot " + action.getDescription() + " an inspection in " + current + "."
            );
        }
        return next;
    }

    private record Transition(InspectionStatus current, InspectionAction action) {
    }
}
