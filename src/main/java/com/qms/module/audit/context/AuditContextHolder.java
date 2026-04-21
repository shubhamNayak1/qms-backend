package com.qms.module.audit.context;

/**
 * Thread-local holder for {@link AuditContext}.
 *
 * Follows the same pattern as Spring's SecurityContextHolder.
 * Always call {@link #clear()} in a finally block to prevent leaks.
 */
public final class AuditContextHolder {

    private static final ThreadLocal<AuditContext> CONTEXT = new ThreadLocal<>();

    private AuditContextHolder() {}

    public static void set(AuditContext context) {
        CONTEXT.set(context);
    }

    public static AuditContext get() {
        return CONTEXT.get();
    }

    public static boolean hasContext() {
        return CONTEXT.get() != null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
