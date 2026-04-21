package com.qms.module.dms.enums;

/**
 * Document lifecycle status.
 *
 * Workflow graph:
 *
 *   DRAFT ──► UNDER_REVIEW ──► APPROVED ──► EFFECTIVE ──► SUPERSEDED
 *               │                  │
 *               └──► REJECTED ──► DRAFT (rework creates new minor version)
 *
 *   EFFECTIVE ──► OBSOLETE  (manually obsoleted by document controller)
 *   DRAFT     ──► WITHDRAWN (author withdraws before review)
 *
 * Terminal states: OBSOLETE, WITHDRAWN, SUPERSEDED
 * Only EFFECTIVE documents are accessible to general users.
 */
public enum DocumentStatus {
    DRAFT,          // created/uploaded, being edited by author
    UNDER_REVIEW,   // submitted for approval
    APPROVED,       // approved but not yet published
    EFFECTIVE,      // live, current version accessible to all permitted users
    REJECTED,       // returned to author for rework
    SUPERSEDED,     // replaced by a newer version (automatically set)
    OBSOLETE,       // retired by a document controller
    WITHDRAWN       // pulled by author before approval
}
