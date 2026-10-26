-- ============================================================
-- V12: Posicao pontual no inicio e na conclusao (EP-07 / PBI-045)
-- Tabela: inspections
-- ============================================================

-- E a posicao do aparelho do tecnico no momento de cada acao, lida uma
-- unica vez (RN-060) -- nao o endereco do local nem o equipamento. Latitude,
-- longitude, precisao e horario da leitura (RN-062). Sem posicao as colunas
-- ficam nulas: nenhuma coordenada e inventada ou aproximada.
ALTER TABLE inspections
    ADD COLUMN start_latitude                  NUMERIC(9, 6),
    ADD COLUMN start_longitude                 NUMERIC(9, 6),
    ADD COLUMN start_accuracy_meters           NUMERIC(10, 2),
    ADD COLUMN start_position_captured_at      TIMESTAMPTZ,
    ADD COLUMN completion_latitude             NUMERIC(9, 6),
    ADD COLUMN completion_longitude            NUMERIC(9, 6),
    ADD COLUMN completion_accuracy_meters      NUMERIC(10, 2),
    ADD COLUMN completion_position_captured_at TIMESTAMPTZ;

-- Uma posicao e completa ou nao existe; a precisao e opcional.
ALTER TABLE inspections ADD CONSTRAINT ck_inspection_start_position CHECK (
    (start_latitude IS NULL AND start_longitude IS NULL
        AND start_accuracy_meters IS NULL AND start_position_captured_at IS NULL)
    OR (
        start_latitude IS NOT NULL AND start_longitude IS NOT NULL
        AND start_position_captured_at IS NOT NULL
        AND start_latitude BETWEEN -90 AND 90
        AND start_longitude BETWEEN -180 AND 180
        AND (start_accuracy_meters IS NULL OR start_accuracy_meters >= 0)
    )
);

ALTER TABLE inspections ADD CONSTRAINT ck_inspection_completion_position CHECK (
    (completion_latitude IS NULL AND completion_longitude IS NULL
        AND completion_accuracy_meters IS NULL AND completion_position_captured_at IS NULL)
    OR (
        completion_latitude IS NOT NULL AND completion_longitude IS NOT NULL
        AND completion_position_captured_at IS NOT NULL
        AND completion_latitude BETWEEN -90 AND 90
        AND completion_longitude BETWEEN -180 AND 180
        AND (completion_accuracy_meters IS NULL OR completion_accuracy_meters >= 0)
    )
);

-- So existe posicao de uma acao que o aparelho registrou (RN-061).
ALTER TABLE inspections ADD CONSTRAINT ck_inspection_start_position_pair
    CHECK (start_latitude IS NULL OR started_at_device IS NOT NULL);
ALTER TABLE inspections ADD CONSTRAINT ck_inspection_completion_position_pair
    CHECK (completion_latitude IS NULL OR completed_at_device IS NOT NULL);
