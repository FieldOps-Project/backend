package com.fieldops.sync.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.sync.domain.model.SyncChange;
import com.fieldops.sync.domain.model.SyncCursor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class SyncChangeReader {

    private static final String BASE_QUERY = """
            WITH scoped_inspections AS (
                SELECT id, client_id, site_id, equipment_id
                FROM inspections
                WHERE technician_id = ?
            ), changes(entity_type, entity_id, change_type, version, changed_at, payload) AS (
                SELECT 'INSPECTION', i.id,
                       CASE WHEN i.status = 'CANCELED' THEN 'DELETE_LOGICAL' ELSE 'UPSERT' END,
                       i.version, i.updated_at,
                       jsonb_build_object(
                           'id', i.id, 'templateVersionId', i.template_version_id,
                           'clientId', i.client_id, 'siteId', i.site_id, 'equipmentId', i.equipment_id,
                           'technicianId', i.technician_id, 'supervisorId', i.supervisor_id,
                           'createdBy', i.created_by, 'title', i.title, 'instructions', i.instructions,
                           'priority', i.priority, 'status', i.status, 'scheduledFor', i.scheduled_for,
                           'startedAtDevice', i.started_at_device, 'startedAtServer', i.started_at_server,
                           'completedAtDevice', i.completed_at_device, 'submittedAtServer', i.submitted_at_server,
                           'approvedAt', i.approved_at, 'canceledAt', i.canceled_at,
                           'canceledBy', i.canceled_by, 'canceledReason', i.canceled_reason,
                           'startPosition', jsonb_build_object(
                               'latitude', i.start_latitude, 'longitude', i.start_longitude,
                               'accuracyMeters', i.start_accuracy_meters,
                               'capturedAtDevice', i.start_position_captured_at
                           ),
                           'completionPosition', jsonb_build_object(
                               'latitude', i.completion_latitude, 'longitude', i.completion_longitude,
                               'accuracyMeters', i.completion_accuracy_meters,
                               'capturedAtDevice', i.completion_position_captured_at
                           ),
                           'createdAt', i.created_at, 'updatedAt', i.updated_at, 'version', i.version
                       )
                FROM inspections i
                WHERE i.technician_id = ?
                UNION ALL
                SELECT 'INSPECTION_SNAPSHOT', s.id, 'UPSERT', 0, s.created_at,
                       jsonb_build_object(
                           'id', s.id, 'inspectionId', s.inspection_id,
                           'sourceTemplateItemId', s.source_template_item_id,
                           'sectionTitle', s.section_title, 'sectionDescription', s.section_description,
                           'sectionOrder', s.section_order, 'itemCode', s.item_code,
                           'itemTitle', s.item_title, 'itemDescription', s.item_description,
                           'responseType', s.response_type, 'required', s.required,
                           'rules', s.rules_json, 'options', s.options_json, 'itemOrder', s.item_order,
                           'createdAt', s.created_at
                       )
                FROM inspection_item_snapshots s
                JOIN scoped_inspections si ON si.id = s.inspection_id
                UNION ALL
                SELECT 'INSPECTION_RESPONSE', r.id, 'UPSERT', r.version, r.updated_at,
                       jsonb_build_object(
                           'id', r.id, 'inspectionId', r.inspection_id,
                           'inspectionItemId', r.inspection_item_id, 'answeredBy', r.answered_by,
                           'valueText', r.value_text, 'valueNumber', r.value_number,
                           'valueBoolean', r.value_boolean, 'valueDate', r.value_date,
                           'valueJson', r.value_json, 'conformity', r.conformity,
                           'observation', r.observation, 'answeredAtDevice', r.answered_at_device,
                           'answeredAtServer', r.answered_at_server, 'baseVersion', r.base_version,
                           'createdAt', r.created_at, 'updatedAt', r.updated_at, 'version', r.version
                       )
                FROM inspection_responses r
                JOIN scoped_inspections si ON si.id = r.inspection_id
                UNION ALL
                SELECT 'INSPECTION_EVIDENCE', e.id,
                       CASE WHEN e.deleted_at IS NULL THEN 'UPSERT' ELSE 'DELETE_LOGICAL' END,
                       e.version, e.updated_at,
                       jsonb_build_object(
                           'id', e.id, 'inspectionId', e.inspection_id,
                           'responseId', e.response_id, 'nonConformityId', e.non_conformity_id,
                           'type', e.type, 'mimeType', e.mime_type, 'sizeBytes', e.size_bytes,
                           'checksum', e.checksum, 'description', e.description,
                           'latitude', e.latitude, 'longitude', e.longitude,
                           'accuracyMeters', e.accuracy_meters, 'capturedAtDevice', e.captured_at_device,
                           'serverReceivedAt', e.server_received_at, 'uploadedAt', e.uploaded_at,
                           'createdBy', e.created_by, 'deletedAt', e.deleted_at, 'deletedBy', e.deleted_by,
                           'updatedAt', e.updated_at, 'version', e.version
                       )
                FROM inspection_evidences e
                JOIN scoped_inspections si ON si.id = e.inspection_id
                UNION ALL
                SELECT 'NON_CONFORMITY', n.id, 'UPSERT', n.version, n.updated_at,
                       jsonb_build_object(
                           'id', n.id, 'inspectionId', n.inspection_id,
                           'inspectionItemId', n.inspection_item_id, 'responseId', n.response_id,
                           'title', n.title, 'description', n.description, 'severity', n.severity,
                           'status', n.status, 'createdBy', n.created_by,
                           'createdAtDevice', n.created_at_device, 'serverReceivedAt', n.server_received_at,
                           'createdAt', n.created_at, 'updatedAt', n.updated_at, 'version', n.version
                       )
                FROM non_conformities n
                JOIN scoped_inspections si ON si.id = n.inspection_id
                UNION ALL
                SELECT 'CLIENT', c.id, 'UPSERT', c.version, c.updated_at,
                       jsonb_build_object(
                           'id', c.id, 'name', c.name, 'legalName', c.legal_name,
                           'document', c.document, 'email', c.email, 'phone', c.phone,
                           'status', c.status, 'createdAt', c.created_at,
                           'updatedAt', c.updated_at, 'version', c.version
                       )
                FROM clients c
                WHERE EXISTS (SELECT 1 FROM scoped_inspections si WHERE si.client_id = c.id)
                UNION ALL
                SELECT 'INSPECTION_SITE', s.id, 'UPSERT', s.version, s.updated_at,
                       jsonb_build_object(
                           'id', s.id, 'clientId', s.client_id, 'name', s.name,
                           'description', s.description, 'addressLine', s.address_line,
                           'city', s.city, 'state', s.state, 'postalCode', s.postal_code,
                           'latitude', s.latitude, 'longitude', s.longitude,
                           'contactName', s.contact_name, 'contactPhone', s.contact_phone,
                           'status', s.status, 'createdAt', s.created_at,
                           'updatedAt', s.updated_at, 'version', s.version
                       )
                FROM inspection_sites s
                WHERE EXISTS (SELECT 1 FROM scoped_inspections si WHERE si.site_id = s.id)
                UNION ALL
                SELECT 'EQUIPMENT', e.id, 'UPSERT', e.version, e.updated_at,
                       jsonb_build_object(
                           'id', e.id, 'siteId', e.site_id, 'name', e.name,
                           'assetNumber', e.asset_number, 'serialNumber', e.serial_number,
                           'manufacturer', e.manufacturer, 'model', e.model,
                           'description', e.description, 'qrCode', e.qr_code,
                           'status', e.status, 'installedAt', e.installed_at,
                           'createdAt', e.created_at, 'updatedAt', e.updated_at, 'version', e.version
                       )
                FROM equipment e
                WHERE EXISTS (SELECT 1 FROM scoped_inspections si WHERE si.equipment_id = e.id)
            )
            SELECT entity_type, entity_id, change_type, version, changed_at, payload::text AS payload
            FROM changes
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SyncChangeReader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<SyncChange> findChanges(UUID technicianId, SyncCursor cursor, int limit) {
        String cursorClause = cursor == null
                ? ""
                : "WHERE (changed_at, entity_id) > (?, ?)\n";
        String sql = BASE_QUERY + cursorClause + "ORDER BY changed_at, entity_id LIMIT ?";
        return jdbcTemplate.query(sql, statement -> bind(statement, technicianId, cursor, limit), rowMapper());
    }

    private void bind(PreparedStatement statement, UUID technicianId, SyncCursor cursor, int limit)
            throws SQLException {
        int index = 1;
        statement.setObject(index++, technicianId);
        statement.setObject(index++, technicianId);
        if (cursor != null) {
            statement.setObject(index++, cursor.changedAt());
            statement.setObject(index++, cursor.entityId());
        }
        statement.setInt(index, limit);
    }

    private RowMapper<SyncChange> rowMapper() {
        return (resultSet, rowNumber) -> new SyncChange(
                resultSet.getString("entity_type"),
                resultSet.getObject("entity_id", UUID.class),
                resultSet.getString("change_type"),
                resultSet.getInt("version"),
                resultSet.getTimestamp("changed_at").toInstant(),
                readPayload(resultSet)
        );
    }

    private JsonNode readPayload(ResultSet resultSet) throws SQLException {
        try {
            return objectMapper.readTree(resultSet.getString("payload"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Stored synchronization payload is invalid", exception);
        }
    }
}
