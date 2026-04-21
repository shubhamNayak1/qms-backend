package com.qms.module.audit.context;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Thread-local context for audit data that cannot be derived from the
 * Security context or method arguments alone.
 *
 * Usage in service code:
 * <pre>
 *   AuditContextHolder.set(AuditContext.builder()
 *       .entityId(user.getId())
 *       .entityType("User")
 *       .description("Password reset by admin")
 *       .build());
 *
 *   // ... perform operation ...
 *
 *   AuditContextHolder.clear();  // always in a finally block
 * </pre>
 *
 * The AuditAspect reads this context when building the log entry,
 * so it takes precedence over annotation defaults.
 */
@Getter
@Setter
@Builder
public class AuditContext {

    private Long   entityId;
    private String entityType;
    private String description;
    private String correlationId;

    /** Pre-serialized JSON snapshot of the entity BEFORE the operation. */
    private String oldValue;

    /** Additional metadata serialized into new_value when no return value is available. */
    private Object additionalData;
}
