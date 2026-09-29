package com.fieldops.inspection.domain.model;

/**
 * Supported response types for template items in the MVP.
 *
 * <p>RN-023 restricts publication to types the mobile app supports.
 * This enum limits what can be persisted, and validation at publish
 * time enforces the rule.</p>
 *
 * <ul>
 *   <li>{@code TEXT_SHORT}    — single-line text input.</li>
 *   <li>{@code TEXT_LONG}     — multi-line text input.</li>
 *   <li>{@code NUMBER}        — numeric value.</li>
 *   <li>{@code BOOLEAN}       — yes / no toggle.</li>
 *   <li>{@code CONFORMITY}    — conforme / nao conforme / nao aplicavel.</li>
 *   <li>{@code SINGLE_CHOICE} — pick one from a list (options stored in {@code options_json}).</li>
 *   <li>{@code DATE}          — date picker.</li>
 * </ul>
 */
public enum ResponseType {
    TEXT_SHORT,
    TEXT_LONG,
    NUMBER,
    BOOLEAN,
    CONFORMITY,
    SINGLE_CHOICE,
    DATE
}
