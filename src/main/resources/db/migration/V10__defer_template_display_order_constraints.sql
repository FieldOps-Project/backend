-- ============================================================
-- V10: Draft editing support (EP-04 / PBI-019 / Issue #13)
-- Batch reordering renumbers every row inside one transaction, so the
-- display_order uniqueness must be checked at commit, not per row.
-- ============================================================

DROP INDEX ux_section_order;
ALTER TABLE template_sections
    ADD CONSTRAINT ux_section_order UNIQUE (template_version_id, display_order)
        DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE template_sections
    ADD CONSTRAINT ck_section_order_positive CHECK (display_order >= 1);

DROP INDEX ux_item_order;
ALTER TABLE template_items
    ADD CONSTRAINT ux_item_order UNIQUE (section_id, display_order)
        DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE template_items
    ADD CONSTRAINT ck_item_order_positive CHECK (display_order >= 1);
