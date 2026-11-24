-- ============================================================
-- V21: Evidence metadata can be applied before its file arrives (EP-08 / PBI-051 / Issue #27)
-- ============================================================
-- server_received_at: metadata applied by the synchronization
-- uploaded_at:        binary received and persisted; NULL while the file is pending (RN-078)

ALTER TABLE inspection_evidences
    ALTER COLUMN uploaded_at DROP NOT NULL;
