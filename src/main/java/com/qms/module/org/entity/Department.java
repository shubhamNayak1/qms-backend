package com.qms.module.org.entity;

import com.qms.common.base.BaseEntity;
import com.qms.module.org.enums.DepartmentType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Department — a node in the org tree.
 *
 * Each department belongs to a Site, has at most one HOD (referenced by
 * users.id), and may be nested via parent_id (sub-departments).
 *
 * dept_type drives QMS workflow gating:
 *   QA       — HOD becomes QA_HEAD; members flagged is_qa_reviewer act as
 *              QA_REVIEWER. The QA department is also the destination for
 *              every record's PENDING_QA_REVIEW step.
 *   RA       — members satisfy the RA position on Deviation / Change Control
 *              flows.
 *   STANDARD — every other department.
 */
@Entity
@Table(
    name = "departments",
    indexes = {
        @Index(name = "idx_dept_site",   columnList = "site_id"),
        @Index(name = "idx_dept_parent", columnList = "parent_id"),
        @Index(name = "idx_dept_type",   columnList = "dept_type"),
        @Index(name = "idx_dept_code",   columnList = "code", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Short stable code (e.g. "QA", "RA", "PROD-A"). Unique across the site. */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "description", length = 500)
    private String description;

    /** Owning site — required. */
    @Column(name = "site_id", nullable = false)
    private Long siteId;

    /** Self-reference for sub-departments. Null = top-level dept under the site. */
    @Column(name = "parent_id")
    private Long parentId;

    /** FK-style reference to users.id — the Head of Department. */
    @Column(name = "hod_user_id")
    private Long hodUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dept_type", nullable = false, length = 20)
    @Builder.Default
    private DepartmentType deptType = DepartmentType.STANDARD;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
