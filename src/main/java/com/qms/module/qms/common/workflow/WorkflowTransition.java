package com.qms.module.qms.common.workflow;

import com.qms.common.enums.QmsStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Defines the allowed status transitions for all QMS record types.
 *
 * Any attempt to move a record to a status not listed here will be
 * rejected by {@link QmsWorkflowEngine} with a 400 Bad Request.
 *
 * The same transition graph applies to ALL five QMS sub-modules,
 * which is why it lives in the common package. Sub-modules can
 * override by providing their own transition map.
 */
public final class WorkflowTransition {

    private WorkflowTransition() {}

    /** Allowed next statuses for each current status. */
    private static final Map<QmsStatus, Set<QmsStatus>> TRANSITIONS =
            new EnumMap<>(QmsStatus.class);

    static {
        TRANSITIONS.put(QmsStatus.OPEN,             Set.of(QmsStatus.IN_PROGRESS, QmsStatus.CANCELLED));
        TRANSITIONS.put(QmsStatus.IN_PROGRESS,      Set.of(QmsStatus.PENDING_APPROVAL, QmsStatus.CANCELLED));
        TRANSITIONS.put(QmsStatus.PENDING_APPROVAL, Set.of(QmsStatus.APPROVED, QmsStatus.REJECTED));
        TRANSITIONS.put(QmsStatus.APPROVED,         Set.of(QmsStatus.CLOSED));
        TRANSITIONS.put(QmsStatus.REJECTED,         Set.of(QmsStatus.IN_PROGRESS, QmsStatus.CANCELLED));
        TRANSITIONS.put(QmsStatus.CLOSED,           Set.of(QmsStatus.REOPENED));
        TRANSITIONS.put(QmsStatus.CANCELLED,        Set.of());       // terminal
        TRANSITIONS.put(QmsStatus.REOPENED,         Set.of(QmsStatus.IN_PROGRESS, QmsStatus.CANCELLED));
    }

    public static boolean isAllowed(QmsStatus from, QmsStatus to) {
        Set<QmsStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        return allowed.contains(to);
    }

    public static Set<QmsStatus> allowedFrom(QmsStatus current) {
        return TRANSITIONS.getOrDefault(current, Set.of());
    }

    public static String transitionError(QmsStatus from, QmsStatus to) {
        return String.format(
                "Cannot transition from %s to %s. Allowed next statuses: %s",
                from, to, allowedFrom(from));
    }
}
