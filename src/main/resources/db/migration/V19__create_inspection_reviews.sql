-- ============================================================
-- V19: One record per review cycle of an inspection (EP-09 / PBI-060 / Issue #28)
-- ============================================================

CREATE TABLE inspection_reviews (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    inspection_id   UUID          NOT NULL,
    reviewer_id     UUID          NOT NULL,
    decision        VARCHAR(16)   NOT NULL,
    reason          VARCHAR(2000),
    comments        VARCHAR(2000),
    reviewed_at     TIMESTAMPTZ   NOT NULL,
    review_cycle    INTEGER       NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_review_inspection FOREIGN KEY (inspection_id) REFERENCES inspections(id),
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id),
    CONSTRAINT ck_review_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_review_reason CHECK (decision <> 'REJECTED' OR (reason IS NOT NULL AND length(trim(reason)) > 0)),
    CONSTRAINT ck_review_cycle CHECK (review_cycle > 0),
    CONSTRAINT ux_review_inspection_cycle UNIQUE (inspection_id, review_cycle)
);

CREATE INDEX ix_review_reviewer ON inspection_reviews (reviewer_id);

CREATE TABLE inspection_review_items (
    review_id           UUID NOT NULL,
    inspection_item_id  UUID NOT NULL,
    CONSTRAINT pk_review_items PRIMARY KEY (review_id, inspection_item_id),
    CONSTRAINT fk_review_items_review FOREIGN KEY (review_id) REFERENCES inspection_reviews(id),
    CONSTRAINT fk_review_items_snapshot FOREIGN KEY (inspection_item_id) REFERENCES inspection_item_snapshots(id)
);
