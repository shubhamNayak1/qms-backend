package com.qms.module.audit.annotation;

import com.qms.common.enums.AuditAction;
import com.qms.common.enums.AuditModule;

import java.lang.annotation.*;

/**
 * Declarative audit trigger.
 *
 * Annotate any Spring-managed service method to have the {@link com.qms.module.audit.aop.AuditAspect}
 * automatically capture a before/after log entry.
 *
 * <pre>
 * // Simple usage — action and module are required:
 * {@literal @}Audited(action = AuditAction.CREATE, module = AuditModule.CAPA)
 * public CapaDto create(CapaRequest request) { ... }
 *
 * // With entity type and description override:
 * {@literal @}Audited(
 *     action      = AuditAction.APPROVE,
 *     module      = AuditModule.DOCUMENT,
 *     entityType  = "Document",
 *     description = "Document approved for distribution"
 * )
 * public DocumentDto approve(Long id) { ... }
 *
 * // Capture old value (for updates/deletes):
 * {@literal @}Audited(action = AuditAction.UPDATE, module = AuditModule.USER,
 *           captureOldValue = true, entityIdArgIndex = 0)
 * public UserDto update(Long id, UpdateUserRequest request) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /** The action being performed. Required. */
    AuditAction action();

    /** The QMS module this action belongs to. Required. */
    AuditModule module();

    /**
     * Simple class name of the affected entity.
     * If empty, the aspect tries to infer it from the return type.
     */
    String entityType() default "";

    /**
     * Human-readable description of the event.
     * If empty, a default description is generated: "{action} on {entityType}".
     */
    String description() default "";

    /**
     * Whether to capture a JSON snapshot of the entity BEFORE the method executes.
     * Useful for UPDATE and DELETE operations.
     * The aspect calls EntitySnapshotProvider if registered, or serializes arg[0].
     */
    boolean captureOldValue() default false;

    /**
     * Whether to capture the method return value as the new_value JSON.
     * Defaults to true for CREATE/UPDATE, false for DELETE/READ.
     */
    boolean captureNewValue() default true;

    /**
     * Index of the method argument that holds the entity ID.
     * -1 means "extract id from the return value".
     * Used to populate entity_id in the audit log.
     */
    int entityIdArgIndex() default -1;

    /**
     * Whether to log this event even when the method throws an exception.
     * When true, outcome is set to FAILURE and the error is recorded.
     */
    boolean logOnFailure() default true;
}
