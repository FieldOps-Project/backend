package com.fieldops.inspection.domain.model;

/**
 * Lifecycle status of an {@link InspectionTemplate}.
 *
 * <ul>
 *   <li>{@code DRAFT}    — template under construction, not yet published.</li>
 *   <li>{@code ACTIVE}   — template available for scheduling new inspections.</li>
 *   <li>{@code INACTIVE} — template retired; existing inspections are preserved.</li>
 * </ul>
 */
public enum TemplateStatus {
    DRAFT,
    ACTIVE,
    INACTIVE
}
