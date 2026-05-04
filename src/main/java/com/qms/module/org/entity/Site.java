package com.qms.module.org.entity;

import com.qms.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Site — the top of the organisational hierarchy.
 *
 * For a single-site deployment we expect exactly one row in this table;
 * `id = 1` is seeded by Flyway. The structure is kept extensible (multiple
 * sites + per-site head) so a future multi-site rollout doesn't require
 * a schema rewrite.
 *
 * The Site Head is referenced as a FK to users.id (no JPA association to
 * avoid a cross-module circular dependency at the entity layer).
 */
@Entity
@Table(name = "sites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Site extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", length = 30, unique = true)
    private String code;

    @Column(name = "address", length = 500)
    private String address;

    /** FK-style reference to users.id — the Site Head (final escalation gate). */
    @Column(name = "head_user_id")
    private Long headUserId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
