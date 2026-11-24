-- ============================================================
-- Demo scenario (EP-10 / PBI-066 / Issue #32)
-- Loaded only by the `demo` profile, which adds classpath:db/demo to the
-- Flyway locations. Schema changes stay in db/migration.
--
-- Every row has a fixed id (literal or derived with md5) and every insert
-- uses ON CONFLICT DO NOTHING (on the id where the order constraint is
-- deferrable), so running the seed again never duplicates
-- records nor overwrites what was changed during a rehearsal.
--
-- Demo credentials (weak on purpose, demo profile only):
--   admin@fieldops.local / supervisor@fieldops.local / tecnico@fieldops.local
--   password: Demo@1234
-- ============================================================

-- 1. Users of the three internal profiles
INSERT INTO users (id, name, email, password_hash, role, status) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'Administrador Demo', 'admin@fieldops.local',
     '$2a$10$l1SBIV75uIWvXBxKlfAavulvANJXrhMucBbbtIxvzqHfCTst4YDUO', 'ADMIN', 'ACTIVE'),
    ('d0000000-0000-4000-8000-000000000002', 'Supervisor Demo', 'supervisor@fieldops.local',
     '$2a$10$l1SBIV75uIWvXBxKlfAavulvANJXrhMucBbbtIxvzqHfCTst4YDUO', 'SUPERVISOR', 'ACTIVE'),
    ('d0000000-0000-4000-8000-000000000003', 'Tecnico Demo', 'tecnico@fieldops.local',
     '$2a$10$l1SBIV75uIWvXBxKlfAavulvANJXrhMucBbbtIxvzqHfCTst4YDUO', 'TECHNICIAN', 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 2. Client, sites and equipment
INSERT INTO clients (id, name, legal_name, email, phone) VALUES
    ('d0000000-0000-4000-8000-000000000101', 'Industria ABC', 'Industria ABC Ltda.',
     'contato@industria-abc.example', '(15) 3000-0000')
ON CONFLICT DO NOTHING;

INSERT INTO inspection_sites (id, client_id, name, description, address_line, city, state, latitude, longitude,
                              contact_name, contact_phone) VALUES
    ('d0000000-0000-4000-8000-000000000201', 'd0000000-0000-4000-8000-000000000101', 'Fabrica Sorocaba',
     'Galpao de producao e casa de maquinas', 'Avenida Industrial, 1000', 'Sorocaba', 'SP', -23.501500, -47.452600,
     'Ana Paula Ferreira', '(15) 99999-0001'),
    ('d0000000-0000-4000-8000-000000000202', 'd0000000-0000-4000-8000-000000000101', 'Unidade Itu',
     'Centro de distribuicao', 'Rua das Industrias, 200', 'Itu', 'SP', -23.264200, -47.299200,
     'Marcos Lima', '(11) 99999-0002')
ON CONFLICT DO NOTHING;

INSERT INTO equipment (id, site_id, name, asset_number, serial_number, manufacturer, model, qr_code, installed_at) VALUES
    ('d0000000-0000-4000-8000-000000000301', 'd0000000-0000-4000-8000-000000000201', 'Gerador GD-001',
     'GD-001', 'SN-GD-458796', 'Geradores Modelo', 'GM-150 kVA', 'FIELDOPS-GD-001', DATE '2021-03-15'),
    ('d0000000-0000-4000-8000-000000000302', 'd0000000-0000-4000-8000-000000000201', 'Empilhadeira EMP-01',
     'EMP-01', 'SN-EMP-102030', 'Empilhadeiras Modelo', 'EM-2.5T', 'FIELDOPS-EMP-01', DATE '2022-07-01'),
    ('d0000000-0000-4000-8000-000000000303', 'd0000000-0000-4000-8000-000000000202', 'Extintor EXT-15',
     'EXT-15', 'SN-EXT-778899', 'Extintores Modelo', 'PQS 6 kg', 'FIELDOPS-EXT-15', DATE '2024-01-10')
ON CONFLICT DO NOTHING;

-- 3. Published template based on Anexo C, covering the seven response types.
--    Version 0 is the editable draft and version 1 its frozen publication (ADR-001).
INSERT INTO inspection_templates (id, title, description, category, status, current_version, created_by) VALUES
    ('d0000000-0000-4000-8000-000000000401', 'Checklist de Inspecao de Geradores',
     'Inspecao preventiva mensal de grupos geradores', 'Geradores', 'ACTIVE', 1,
     'd0000000-0000-4000-8000-000000000002')
ON CONFLICT DO NOTHING;

INSERT INTO inspection_template_versions (id, template_id, version_number, title_snapshot, description_snapshot,
                                          published_by, published_at, active_for_new_inspections) VALUES
    ('d0000000-0000-4000-8000-000000000410', 'd0000000-0000-4000-8000-000000000401', 0,
     'Checklist de Inspecao de Geradores', 'Inspecao preventiva mensal de grupos geradores', NULL, NULL, FALSE),
    ('d0000000-0000-4000-8000-000000000411', 'd0000000-0000-4000-8000-000000000401', 1,
     'Checklist de Inspecao de Geradores', 'Inspecao preventiva mensal de grupos geradores',
     'd0000000-0000-4000-8000-000000000002', now(), TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO template_sections (id, template_version_id, title, description, display_order)
SELECT md5(v.id || '-section-' || s.display_order)::uuid, v.id, s.title, s.description, s.display_order
FROM (VALUES ('d0000000-0000-4000-8000-000000000410'::uuid),
             ('d0000000-0000-4000-8000-000000000411'::uuid)) AS v(id)
CROSS JOIN (VALUES
    (1, 'Condicoes gerais', 'Inspecao visual do equipamento'),
    (2, 'Seguranca', 'Dispositivos e sinalizacao de seguranca'),
    (3, 'Funcionamento', 'Medicoes com o gerador em operacao'),
    (4, 'Registro', 'Dados da ordem de servico e parecer')
) AS s(display_order, title, description)
ON CONFLICT (id) DO NOTHING;

INSERT INTO template_items (id, section_id, code, title, description, response_type, required,
                            observation_required_on_failure, evidence_required_on_failure, options_json, display_order)
SELECT md5(v.id || '-item-' || i.code)::uuid, md5(v.id || '-section-' || i.section_order)::uuid,
       i.code, i.title, i.description, i.response_type, i.required, i.observation_on_failure,
       i.evidence_on_failure, i.options_json, i.display_order
FROM (VALUES ('d0000000-0000-4000-8000-000000000410'::uuid),
             ('d0000000-0000-4000-8000-000000000411'::uuid)) AS v(id)
CROSS JOIN (VALUES
    (1, 1, 'GER-GER-001', 'Placa de identificacao',
     'A placa de identificacao esta presente, legivel e fixada corretamente?',
     'CONFORMITY', TRUE, FALSE, FALSE, NULL),
    (1, 2, 'GER-GER-002', 'Limpeza e conservacao',
     'O equipamento apresenta condicoes adequadas de limpeza e conservacao?',
     'CONFORMITY', TRUE, TRUE, TRUE, NULL),
    (1, 3, 'GER-GER-003', 'Estado da carenagem',
     'Qual o estado da carenagem e da estrutura externa?',
     'SINGLE_CHOICE', TRUE, FALSE, FALSE, '["Bom","Regular","Ruim"]'),
    (2, 1, 'GER-SEG-001', 'Botao de emergencia',
     'O botao de parada de emergencia esta acessivel e desligou o gerador no teste?',
     'BOOLEAN', TRUE, TRUE, FALSE, NULL),
    (2, 2, 'GER-SEG-002', 'Sinalizacao de seguranca',
     'As etiquetas e sinalizacoes de advertencia estao visiveis e legiveis?',
     'CONFORMITY', TRUE, TRUE, FALSE, NULL),
    (3, 1, 'GER-FUN-001', 'Tensao de saida (V)',
     'Qual a tensao de saida medida entre fases?',
     'NUMBER', TRUE, FALSE, FALSE, NULL),
    (3, 2, 'GER-FUN-002', 'Horimetro (h)',
     'Qual a leitura atual do horimetro?',
     'NUMBER', TRUE, FALSE, FALSE, NULL),
    (3, 3, 'GER-FUN-003', 'Data da ultima manutencao',
     'Data registrada na etiqueta da ultima manutencao',
     'DATE', TRUE, FALSE, FALSE, NULL),
    (4, 1, 'GER-REG-001', 'Ordem de servico',
     'Numero da ordem de servico',
     'TEXT_SHORT', TRUE, FALSE, FALSE, NULL),
    (4, 2, 'GER-REG-002', 'Parecer do tecnico',
     'Resumo das condicoes encontradas e acoes recomendadas',
     'TEXT_LONG', FALSE, FALSE, FALSE, NULL)
) AS i(section_order, display_order, code, title, description, response_type, required,
       observation_on_failure, evidence_on_failure, options_json)
ON CONFLICT (id) DO NOTHING;

-- 4. Inspections in the states the dashboard shows, all on the generator.
--    Times are relative to the first run, so the assigned one is scheduled for that day.
INSERT INTO inspections (id, template_version_id, client_id, site_id, equipment_id, technician_id, supervisor_id,
                         created_by, title, instructions, priority, status, scheduled_for,
                         started_at_device, started_at_server, completed_at_device, submitted_at_server, approved_at)
SELECT i.id, 'd0000000-0000-4000-8000-000000000411', 'd0000000-0000-4000-8000-000000000101',
       'd0000000-0000-4000-8000-000000000201', 'd0000000-0000-4000-8000-000000000301',
       'd0000000-0000-4000-8000-000000000003', 'd0000000-0000-4000-8000-000000000002',
       'd0000000-0000-4000-8000-000000000002', i.title, i.instructions, i.priority, i.status, i.scheduled_for,
       CASE WHEN i.status <> 'ASSIGNED' THEN i.scheduled_for + interval '10 minutes' END,
       CASE WHEN i.status <> 'ASSIGNED' THEN i.scheduled_for + interval '10 minutes' END,
       CASE WHEN i.status IN ('SUBMITTED', 'APPROVED', 'REJECTED') THEN i.scheduled_for + interval '1 hour' END,
       CASE WHEN i.status IN ('SUBMITTED', 'APPROVED', 'REJECTED') THEN i.scheduled_for + interval '1 hour' END,
       CASE WHEN i.status = 'APPROVED' THEN i.scheduled_for + interval '1 day' END
FROM (VALUES
    ('d0000000-0000-4000-8000-000000000501'::uuid, 'Inspecao preventiva - Gerador GD-001',
     'Inspecao da demonstracao: leia o QR Code FIELDOPS-GD-001 no gerador.', 'HIGH', 'ASSIGNED',
     date_trunc('day', now()) + interval '14 hours'),
    ('d0000000-0000-4000-8000-000000000502'::uuid, 'Inspecao preventiva semanal - Gerador GD-001',
     NULL, 'MEDIUM', 'IN_PROGRESS', date_trunc('day', now()) - interval '1 day' + interval '9 hours'),
    ('d0000000-0000-4000-8000-000000000503'::uuid, 'Inspecao apos manutencao - Gerador GD-001',
     NULL, 'MEDIUM', 'SUBMITTED', date_trunc('day', now()) - interval '3 days' + interval '9 hours'),
    ('d0000000-0000-4000-8000-000000000504'::uuid, 'Inspecao preventiva mensal - Gerador GD-001',
     NULL, 'MEDIUM', 'APPROVED', date_trunc('day', now()) - interval '10 days' + interval '9 hours'),
    ('d0000000-0000-4000-8000-000000000505'::uuid, 'Inspecao de rotina - Gerador GD-001',
     NULL, 'LOW', 'REJECTED', date_trunc('day', now()) - interval '5 days' + interval '9 hours')
) AS i(id, title, instructions, priority, status, scheduled_for)
ON CONFLICT DO NOTHING;

-- Snapshots copied from the published version, as scheduling does (RN-021).
INSERT INTO inspection_item_snapshots (id, inspection_id, source_template_item_id, section_title, section_description,
                                       section_order, item_code, item_title, item_description, response_type,
                                       required, rules_json, options_json, item_order)
SELECT md5(i.id || '-snapshot-' || ti.code)::uuid, i.id, ti.id, ts.title, ts.description, ts.display_order,
       ti.code, ti.title, ti.description, ti.response_type, ti.required,
       jsonb_build_object('observationRequiredOnFailure', ti.observation_required_on_failure,
                          'evidenceRequiredOnFailure', ti.evidence_required_on_failure),
       ti.options_json::jsonb, ti.display_order
FROM inspections i
JOIN template_sections ts ON ts.template_version_id = i.template_version_id
JOIN template_items ti ON ti.section_id = ts.id
WHERE i.id IN ('d0000000-0000-4000-8000-000000000501', 'd0000000-0000-4000-8000-000000000502',
               'd0000000-0000-4000-8000-000000000503', 'd0000000-0000-4000-8000-000000000504',
               'd0000000-0000-4000-8000-000000000505')
ON CONFLICT DO NOTHING;

-- 5. Responses: partial for IN_PROGRESS (first section), complete for the others.
--    The approved inspection has a non-conforming safety sign; the rejected one an
--    hour meter reading that went backwards.
INSERT INTO inspection_responses (id, inspection_id, inspection_item_id, answered_by, value_text, value_number,
                                  value_boolean, value_date, conformity, observation, answered_at_device)
SELECT md5(i.id || '-response-' || a.code)::uuid, i.id, md5(i.id || '-snapshot-' || a.code)::uuid,
       'd0000000-0000-4000-8000-000000000003',
       a.value_text,
       CASE WHEN i.id = 'd0000000-0000-4000-8000-000000000505' AND a.code = 'GER-FUN-002' THEN 1200
            ELSE a.value_number END,
       a.value_boolean, a.value_date,
       CASE WHEN i.id = 'd0000000-0000-4000-8000-000000000504' AND a.code = 'GER-SEG-002' THEN 'NON_CONFORMING'
            ELSE a.conformity END,
       CASE WHEN i.id = 'd0000000-0000-4000-8000-000000000504' AND a.code = 'GER-SEG-002'
            THEN 'Etiqueta de risco eletrico parcialmente apagada.' END,
       i.started_at_device + interval '5 minutes'
FROM inspections i
CROSS JOIN (VALUES
    ('GER-GER-001', NULL, NULL::numeric, NULL::boolean, NULL::date, 'CONFORMING'),
    ('GER-GER-002', NULL, NULL, NULL, NULL, 'CONFORMING'),
    ('GER-GER-003', 'Bom', NULL, NULL, NULL, NULL),
    ('GER-SEG-001', NULL, NULL, TRUE, NULL, NULL),
    ('GER-SEG-002', NULL, NULL, NULL, NULL, 'CONFORMING'),
    ('GER-FUN-001', NULL, 380, NULL, NULL, NULL),
    ('GER-FUN-002', NULL, 3845, NULL, NULL, NULL),
    ('GER-FUN-003', NULL, NULL, NULL, DATE '2026-05-10', NULL),
    ('GER-REG-001', 'OS-2026-0187', NULL, NULL, NULL, NULL),
    ('GER-REG-002', 'Gerador em condicoes normais de operacao.', NULL, NULL, NULL, NULL)
) AS a(code, value_text, value_number, value_boolean, value_date, conformity)
WHERE i.id IN ('d0000000-0000-4000-8000-000000000503', 'd0000000-0000-4000-8000-000000000504',
               'd0000000-0000-4000-8000-000000000505')
   OR (i.id = 'd0000000-0000-4000-8000-000000000502' AND a.code LIKE 'GER-GER-%')
ON CONFLICT DO NOTHING;

-- 6. Open non-conformity of the approved inspection
INSERT INTO non_conformities (id, inspection_id, inspection_item_id, response_id, title, description, severity,
                              status, created_by, created_at_device, server_received_at)
SELECT 'd0000000-0000-4000-8000-000000000601', i.id,
       md5(i.id || '-snapshot-GER-SEG-002')::uuid, md5(i.id || '-response-GER-SEG-002')::uuid,
       'Etiqueta de seguranca danificada', 'Etiqueta de risco eletrico parcialmente ilegivel; substituir a etiqueta.',
       'MEDIUM', 'OPEN', 'd0000000-0000-4000-8000-000000000003',
       i.started_at_device + interval '6 minutes', i.started_at_server + interval '6 minutes'
FROM inspections i
WHERE i.id = 'd0000000-0000-4000-8000-000000000504'
ON CONFLICT DO NOTHING;

-- 7. Review cycles: approval and motivated rejection with the item to correct
INSERT INTO inspection_reviews (id, inspection_id, reviewer_id, decision, reason, comments, reviewed_at, review_cycle)
SELECT r.id, i.id, 'd0000000-0000-4000-8000-000000000002', r.decision, r.reason, r.comments,
       i.submitted_at_server + interval '1 day', 1
FROM inspections i
JOIN (VALUES
    ('d0000000-0000-4000-8000-000000000701'::uuid, 'd0000000-0000-4000-8000-000000000504'::uuid, 'APPROVED',
     NULL, 'Aprovada; acompanhar a substituicao da etiqueta pela nao conformidade aberta.'),
    ('d0000000-0000-4000-8000-000000000702'::uuid, 'd0000000-0000-4000-8000-000000000505'::uuid, 'REJECTED',
     'Leitura do horimetro menor que a da ultima inspecao.', 'Refazer a leitura do horimetro.')
) AS r(id, inspection_id, decision, reason, comments) ON r.inspection_id = i.id
ON CONFLICT DO NOTHING;

INSERT INTO inspection_review_items (review_id, inspection_item_id) VALUES
    ('d0000000-0000-4000-8000-000000000702',
     md5('d0000000-0000-4000-8000-000000000505-snapshot-GER-FUN-002')::uuid)
ON CONFLICT DO NOTHING;
