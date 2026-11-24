package com.fieldops.sync.domain.model;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.fieldops.shared.domain.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Opaque server cursor backed by the ordered pair (changedAt, entityId). */
public record SyncCursor(Instant changedAt, UUID entityId) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String encode() {
        String json = "{\"ts\":\"" + changedAt + "\",\"id\":\"" + entityId + "\"}";
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static SyncCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(encoded);
            var payload = OBJECT_MAPPER.readTree(json);
            if (!payload.hasNonNull("ts") || !payload.hasNonNull("id")) {
                throw invalid();
            }
            return new SyncCursor(
                    Instant.parse(payload.get("ts").asText()),
                    UUID.fromString(payload.get("id").asText())
            );
        } catch (IllegalArgumentException | JacksonException exception) {
            throw invalid();
        }
    }

    private static InvalidRequestException invalid() {
        return new InvalidRequestException(
                "SYNC_CURSOR_INVALID",
                "The synchronization cursor is invalid"
        );
    }
}
