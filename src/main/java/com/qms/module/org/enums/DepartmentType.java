package com.qms.module.org.enums;

/**
 * Categorises a department for QMS workflow gating.
 *
 * QA  — Quality Assurance — gates the central review path. The HOD of the QA
 *        department is the QA_HEAD (final approver). Members flagged
 *        is_qa_reviewer act as QA_REVIEWERs.
 *
 * RA  — Regulatory Affairs — gates the RA review step on Deviations / Change
 *        Controls. Members of any RA department satisfy the RA position.
 *
 * STANDARD — every other department (Production, IT, HR, Engineering, etc.)
 *            Their HOD is just "HOD of that dept"; their members are
 *            INITIATORs by default.
 */
public enum DepartmentType {
    QA,
    RA,
    STANDARD
}
