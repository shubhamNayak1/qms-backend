package com.qms.module.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Converts any Java object to a JSON string for storage in audit_logs.old_value / new_value.
 *
 * Sensitive field protection:
 *   Any field whose name (case-insensitive) appears in the sensitive-fields config list
 *   is replaced with "***MASKED***" before the JSON is stored.
 *   This prevents passwords, tokens, and card numbers from appearing in audit logs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditValueSerializer {

    private static final String MASK        = "***MASKED***";
    private static final int    MAX_LENGTH  = 65_000;  // TEXT column safe limit

    private final ObjectMapper objectMapper;

    @Value("${audit.sensitive-fields:password,passwordHash,refreshTokenHash,creditCard,cvv}")
    private List<String> sensitiveFields;

    /**
     * Serializes the value to JSON, masking sensitive fields.
     * Returns null (not "null") if the value is null — null is stored as SQL NULL.
     */
    public String serialize(Object value) {
        if (value == null) return null;

        try {
            // Convert to JsonNode so we can manipulate fields
            var node = objectMapper.valueToTree(value);

            if (node.isObject()) {
                maskSensitiveFields((ObjectNode) node);
            }

            String json = objectMapper.writeValueAsString(node);

            // Truncate if too large — e.g. very large document metadata
            if (json.length() > MAX_LENGTH) {
                log.warn("Audit value truncated — original length: {} chars", json.length());
                return json.substring(0, MAX_LENGTH - 20) + "...[TRUNCATED]";
            }

            return json;

        } catch (JsonProcessingException e) {
            log.warn("Could not serialize audit value of type {}: {}",
                    value.getClass().getSimpleName(), e.getMessage());
            return "{\"_error\":\"serialization failed\",\"type\":\"" +
                    value.getClass().getSimpleName() + "\"}";
        }
    }

    /**
     * Convenience: serialize only if the value is not null, otherwise return null.
     * Used when the old/new capture is optional.
     */
    public String serializeIfPresent(Object value) {
        return value == null ? null : serialize(value);
    }

    // ─── Helpers ─────────────────────────────────────────────

    private void maskSensitiveFields(ObjectNode node) {
        node.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey().toLowerCase(Locale.ROOT);
            boolean isSensitive = sensitiveFields.stream()
                    .anyMatch(s -> fieldName.contains(s.toLowerCase(Locale.ROOT)));
            if (isSensitive) {
                node.put(entry.getKey(), MASK);
            } else if (entry.getValue().isObject()) {
                maskSensitiveFields((ObjectNode) entry.getValue());
            }
        });
    }
}
