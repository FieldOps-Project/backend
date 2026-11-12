package com.fieldops.inspection.domain.service;

import com.fieldops.inspection.domain.model.InspectionAction;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.shared.domain.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionStateMachineTest {

    private final InspectionStateMachine stateMachine = new InspectionStateMachine();

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void acceptsEveryTransitionFromTheBusinessTable(
            InspectionStatus current,
            InspectionAction action,
            InspectionStatus expected
    ) {
        assertThat(stateMachine.assertTransitionValid(current, action)).isEqualTo(expected);
    }

    @Test
    void rejectsAnUnsupportedTransitionWithTheConflictCode() {
        assertThatThrownBy(() -> stateMachine.assertTransitionValid(
                InspectionStatus.APPROVED,
                InspectionAction.CANCEL
        ))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_INVALID_TRANSITION");
    }

    @Test
    void rejectsCancelingAnInProgressInspectionWhenTheActionIsNotAllowed() {
        assertThatThrownBy(() -> stateMachine.assertTransitionValid(
                InspectionStatus.SUBMITTED,
                InspectionAction.CANCEL
        ))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_INVALID_TRANSITION");
    }

    @Test
    void rejectsStartingAnAlreadyAssignedInspectionMoreThanOnce() {
        assertThatThrownBy(() -> stateMachine.assertTransitionValid(
                InspectionStatus.IN_PROGRESS,
                InspectionAction.START
        ))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "INSPECTION_INVALID_TRANSITION");
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(InspectionStatus.DRAFT, InspectionAction.ASSIGN, InspectionStatus.ASSIGNED),
                Arguments.of(InspectionStatus.ASSIGNED, InspectionAction.START, InspectionStatus.IN_PROGRESS),
                Arguments.of(InspectionStatus.ASSIGNED, InspectionAction.CANCEL, InspectionStatus.CANCELED),
                Arguments.of(InspectionStatus.IN_PROGRESS, InspectionAction.SUBMIT, InspectionStatus.SUBMITTED),
                Arguments.of(InspectionStatus.IN_PROGRESS, InspectionAction.CANCEL, InspectionStatus.CANCELED),
                Arguments.of(InspectionStatus.SUBMITTED, InspectionAction.START_REVIEW, InspectionStatus.UNDER_REVIEW),
                Arguments.of(InspectionStatus.UNDER_REVIEW, InspectionAction.APPROVE, InspectionStatus.APPROVED),
                Arguments.of(InspectionStatus.UNDER_REVIEW, InspectionAction.REJECT, InspectionStatus.REJECTED),
                Arguments.of(InspectionStatus.REJECTED, InspectionAction.REOPEN, InspectionStatus.IN_PROGRESS),
                Arguments.of(InspectionStatus.REJECTED, InspectionAction.CANCEL, InspectionStatus.CANCELED)
        );
    }
}
