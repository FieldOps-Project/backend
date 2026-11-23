package com.fieldops.inspection.domain.model;

/**
 * Actions that can move an inspection through its business lifecycle.
 *
 * <p>The action is deliberately independent from the HTTP endpoints so every
 * use case can use the same transition policy.</p>
 */
public enum InspectionAction {
    ASSIGN("assign"),
    START("start"),
    SUBMIT("submit"),
    CANCEL("cancel"),
    START_REVIEW("start review"),
    APPROVE("approve"),
    REJECT("reject"),
    REOPEN("reopen");

    private final String description;

    InspectionAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
