-- An inspection can be prepared before assignment. Assigned and later states
-- must still have a responsible technician.
ALTER TABLE inspections ALTER COLUMN technician_id DROP NOT NULL;

ALTER TABLE inspections
    ADD CONSTRAINT ck_inspection_technician_for_assigned
    CHECK (status = 'DRAFT' OR technician_id IS NOT NULL);
