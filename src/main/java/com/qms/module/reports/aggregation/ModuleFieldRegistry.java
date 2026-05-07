package com.qms.module.reports.aggregation;

import com.qms.common.exception.AppException;
import com.qms.module.reports.enums.ReportModule;
import lombok.Getter;

import java.util.*;

/**
 * Registry of available dimensions and metrics for each report module.
 *
 * Dimension  — a categorical field used to GROUP data (e.g. status, department).
 *              Each unique combination of dimension values becomes one result row.
 * Metric     — an informational field displayed per row. For grouped reports these
 *              are aggregate expressions (COUNT, AVG). For detail reports they are
 *              plain column projections.
 */
public class ModuleFieldRegistry {

    /** A single field definition. */
    @Getter
    public static class FieldDef {
        private final String key;        // API key sent by frontend
        private final String label;      // Human-readable label
        private final String sqlExpr;    // SQL column expression or alias
        private final boolean dimension; // true = can group by; false = metric only

        public FieldDef(String key, String label, String sqlExpr, boolean dimension) {
            this.key = key; this.label = label; this.sqlExpr = sqlExpr; this.dimension = dimension;
        }
    }

    // ── Module → (tableName, List<FieldDef>) ──────────────────

    private static final Map<ReportModule, String> TABLE_MAP = new EnumMap<>(ReportModule.class);
    private static final Map<ReportModule, List<FieldDef>> FIELD_MAP = new EnumMap<>(ReportModule.class);

    static {
        // ── CAPA ──────────────────────────────────────────────
        TABLE_MAP.put(ReportModule.CAPA, "qms_capa");
        FIELD_MAP.put(ReportModule.CAPA, List.of(
            // Dimensions
            dim("status",                    "Status",                "status"),
            dim("priority",                  "Priority",              "priority"),
            dim("source",                    "Source",                "source"),
            dim("capa_type",                 "CAPA Type",             "capa_type"),
            dim("capa_origin",               "Origin",                "capa_origin"),                  // V24
            dim("parent_record_type",        "Parent Module",         "parent_record_type"),           // V24
            dim("department",                "Department",            "department"),
            dim("site_head_required",        "Site Head Required",    "CASE WHEN site_head_required THEN 'Yes' ELSE 'No' END"),  // V24
            dim("assessment_frequency",      "Assessment Frequency",  "assessment_frequency"),         // V24
            dim("assessment_summary_status", "Assessment Status",     "assessment_summary_status"),    // V24
            dim("month",                     "Month",                 "TO_CHAR(created_at,'YYYY-MM')"),
            dim("quarter",                   "Quarter",               "CONCAT(EXTRACT(YEAR FROM created_at),'-Q',CAST(CEIL(EXTRACT(MONTH FROM created_at)/3.0) AS INTEGER))"),
            dim("year",                      "Year",                  "CAST(EXTRACT(YEAR FROM created_at) AS TEXT)"),
            // Metrics
            met("record_number",             "Record #",              "record_number"),
            met("title",                     "Title",                 "title"),
            met("parent_record_number",      "Parent Record #",       "parent_record_number"),         // V24
            met("linked_deviation_number",   "Linked Deviation # (legacy)", "linked_deviation_number"),
            met("assigned_to",               "Assigned To",           "assigned_to_name"),
            met("raised_by",                 "Raised By",             "raised_by_name"),
            met("due_date",                  "Due Date",              "due_date"),
            met("closed_date",               "Closed Date",           "closed_date"),
            met("assessment_count",          "Assessment Count",      "assessment_count"),             // V24
            met("age_days",                  "Age (Days)",            "COALESCE(CAST(EXTRACT(DAY FROM NOW()-created_at) AS INTEGER),0)"),
            met("is_effective",              "Effective?",            "CASE WHEN is_effective THEN 'Yes' WHEN is_effective=false THEN 'No' ELSE '' END"),
            met("overdue",                   "Overdue?",              "CASE WHEN due_date < NOW() AND status NOT IN ('CLOSED','CANCELLED','EFFECTIVENESS_VERIFIED') THEN 'Yes' ELSE 'No' END")
        ));

        // ── DEVIATION ─────────────────────────────────────────
        TABLE_MAP.put(ReportModule.DEVIATION, "qms_deviation");
        FIELD_MAP.put(ReportModule.DEVIATION, List.of(
            dim("status",                    "Status",                "status"),
            dim("priority",                  "Priority",              "priority"),
            dim("deviation_type",            "Deviation Type",        "deviation_type"),
            dim("process_area",              "Process Area",          "process_area"),
            dim("department",                "Department",            "department"),
            dim("regulatory_reportable",     "Regulatory Reportable", "CASE WHEN regulatory_reportable THEN 'Yes' ELSE 'No' END"),
            dim("capa_required",             "CAPA Required",         "CASE WHEN capa_required THEN 'Yes' ELSE 'No' END"),     // V22
            dim("site_head_required",        "Site Head Required",    "CASE WHEN site_head_required THEN 'Yes' ELSE 'No' END"), // V22
            dim("customer_comment_required", "Customer Comment Req",  "CASE WHEN customer_comment_required THEN 'Yes' ELSE 'No' END"), // V22
            dim("month",                     "Month",                 "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",                      "Year",                  "CAST(EXTRACT(YEAR FROM created_at) AS TEXT)"),
            met("record_number",             "Record #",              "record_number"),
            met("title",                     "Title",                 "title"),
            met("product_batch",             "Product / Batch",       "product_batch"),
            met("parent_incident_id",        "Parent Incident ID",    "parent_incident_id"),    // V22
            met("parent_incident_number",    "Parent Incident #",     "(SELECT record_number FROM qms_incident WHERE id = qms_deviation.parent_incident_id)"),
            met("linked_capa_number",        "Linked CAPA #",         "linked_capa_number"),    // V22
            met("assigned_to",               "Assigned To",           "assigned_to_name"),
            met("raised_by",                 "Raised By",             "raised_by_name"),
            met("due_date",                  "Due Date",              "due_date"),
            met("closed_date",               "Closed Date",           "closed_date"),
            met("age_days",                  "Age (Days)",            "COALESCE(CAST(EXTRACT(DAY FROM NOW()-created_at) AS INTEGER),0)"),
            met("overdue",                   "Overdue?",              "CASE WHEN due_date < NOW() AND status NOT IN ('CLOSED','CANCELLED') THEN 'Yes' ELSE 'No' END")
        ));

        // ── INCIDENT ──────────────────────────────────────────
        TABLE_MAP.put(ReportModule.INCIDENT, "qms_incident");
        FIELD_MAP.put(ReportModule.INCIDENT, List.of(
            dim("status",                "Status",               "status"),
            dim("severity",              "Severity",             "severity"),
            dim("incident_type",         "Incident Type",        "incident_type"),
            dim("incident_sub_type",     "Sub-Type",             "incident_sub_type"),  // V11
            dim("location",              "Location",             "location"),
            dim("department",            "Department",           "department"),
            dim("injury_involved",       "Injury Involved",      "CASE WHEN injury_involved THEN 'Yes' ELSE 'No' END"),
            dim("retesting_required",    "Retesting Required",   "CASE WHEN retesting_required THEN 'Yes' ELSE 'No' END"),  // V11
            dim("deviation_required",    "Deviation Required",   "CASE WHEN deviation_required THEN 'Yes' ELSE 'No' END"),  // V11
            dim("capa_required",         "CAPA Required",        "CASE WHEN capa_required THEN 'Yes' ELSE 'No' END"),       // V23
            dim("site_head_required",    "Site Head Required",   "CASE WHEN site_head_required THEN 'Yes' ELSE 'No' END"),  // V23
            dim("month",                 "Month",                "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",                  "Year",                 "CAST(EXTRACT(YEAR FROM created_at) AS TEXT)"),
            met("record_number",         "Record #",             "record_number"),
            met("title",                 "Title",                "title"),
            met("linked_capa_number",    "Linked CAPA #",        "linked_capa_number"),         // V23
            met("spawned_deviation_number","Spawned Deviation #", "spawned_deviation_number"),  // V23
            met("assigned_to",           "Assigned To",          "assigned_to_name"),
            met("reported_by",           "Reported By",          "reported_by"),
            met("occurrence_date",       "Occurrence Date",      "occurrence_date"),
            met("due_date",              "Due Date",             "due_date"),
            met("closed_date",           "Closed Date",          "closed_date"),
            met("age_days",              "Age (Days)",           "COALESCE(CAST(EXTRACT(DAY FROM NOW()-created_at) AS INTEGER),0)"),
            met("overdue",               "Overdue?",             "CASE WHEN due_date < NOW() AND status NOT IN ('CLOSED','CANCELLED','DEVIATION_SPAWNED') THEN 'Yes' ELSE 'No' END")
        ));

        // ── CHANGE_CONTROL ────────────────────────────────────
        TABLE_MAP.put(ReportModule.CHANGE_CONTROL, "qms_change_control");
        FIELD_MAP.put(ReportModule.CHANGE_CONTROL, List.of(
            dim("status",                       "Status",               "status"),
            dim("priority",                     "Priority",             "priority"),
            dim("change_type",                  "Change Type",          "change_type"),
            dim("risk_level",                   "Risk Level",           "risk_level"),
            dim("department",                   "Department",           "department"),
            dim("category",                     "Category (RA)",        "category"),
            dim("site_head_required",           "Site Head Required",   "CASE WHEN site_head_required THEN 'Yes' ELSE 'No' END"),
            dim("regulatory_submission_required","Regulatory Submission","CASE WHEN regulatory_submission_required THEN 'Yes' ELSE 'No' END"),
            dim("validation_required",          "Validation Required",  "CASE WHEN validation_required THEN 'Yes' ELSE 'No' END"),
            dim("month",                        "Month",                "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",                         "Year",                 "CAST(EXTRACT(YEAR FROM created_at) AS TEXT)"),
            met("record_number",                "Record #",             "record_number"),
            met("title",                        "Title",                "title"),
            met("product_material",             "Product / Material",   "product_material"),    // V20
            met("market_details",               "Market Details",       "market_details"),      // V20
            met("linked_capa_number",           "Linked CAPA #",        "linked_capa_number"),  // V20
            met("raised_by",                    "Raised By",            "raised_by_name"),
            met("assigned_to",                  "Assigned To",          "assigned_to_name"),
            met("due_date",                     "Due Date",             "due_date"),
            met("closed_date",                  "Closed Date",          "closed_date"),
            met("age_days",                     "Age (Days)",           "COALESCE(CAST(EXTRACT(DAY FROM NOW()-created_at) AS INTEGER),0)"),
            met("overdue",                      "Overdue?",             "CASE WHEN due_date < NOW() AND status NOT IN ('CLOSED','CANCELLED') THEN 'Yes' ELSE 'No' END")
        ));

        // ── COMPLAINT ─────────────────────────────────────────
        TABLE_MAP.put(ReportModule.COMPLAINT, "qms_market_complaint");
        FIELD_MAP.put(ReportModule.COMPLAINT, List.of(
            dim("status",                    "Status",                "status"),
            dim("priority",                  "Priority",              "priority"),
            dim("complaint_origin",          "Origin",                "complaint_origin"),       // V21
            dim("complaint_subject",         "Subject",               "complaint_subject"),      // V21
            dim("complaint_category",        "Category",              "complaint_category"),
            dim("complaint_source",          "Source",                "complaint_source"),
            dim("customer_country",          "Country",               "customer_country"),
            dim("department",                "Department",            "department"),
            dim("reportable_to_authority",   "Reportable?",           "CASE WHEN reportable_to_authority THEN 'Yes' ELSE 'No' END"),
            dim("capa_required",             "CAPA Required",         "CASE WHEN capa_required THEN 'Yes' ELSE 'No' END"),  // V21
            dim("sample_returned",           "Sample Returned",       "CASE WHEN sample_returned THEN 'Yes' ELSE 'No' END"),
            dim("customer_satisfied",        "Customer Satisfied",    "CASE WHEN customer_satisfied THEN 'Yes' WHEN customer_satisfied=false THEN 'No' ELSE '' END"),
            dim("month",                     "Month",                 "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",                      "Year",                  "CAST(EXTRACT(YEAR FROM created_at) AS TEXT)"),
            met("record_number",             "Record #",              "record_number"),
            met("title",                     "Title",                 "title"),
            met("parent_complaint_id",       "Parent Complaint ID",   "parent_complaint_id"),    // V21
            met("parent_complaint_number",   "Parent Complaint #",    "(SELECT record_number FROM qms_market_complaint p WHERE p.id = qms_market_complaint.parent_complaint_id)"),
            met("customer_name",             "Customer",              "customer_name"),
            met("product_name",              "Product",               "product_name"),
            met("batch_number",              "Batch #",               "batch_number"),
            met("capa_reference",            "Linked CAPA #",         "capa_reference"),         // V21
            met("assigned_to",               "Assigned To",           "assigned_to_name"),
            met("received_date",             "Received Date",         "received_date"),
            met("due_date",                  "Due Date",              "due_date"),
            met("closed_date",               "Closed Date",           "closed_date"),
            met("age_days",                  "Age (Days)",            "COALESCE(CAST(EXTRACT(DAY FROM NOW()-created_at) AS INTEGER),0)")
        ));

        // ── LMS_ENROLLMENT ────────────────────────────────────
        TABLE_MAP.put(ReportModule.LMS_ENROLLMENT, "lms_enrollments");
        FIELD_MAP.put(ReportModule.LMS_ENROLLMENT, List.of(
            dim("status",            "Enrollment Status", "status"),
            dim("training_kind",     "Training Kind",     "CASE WHEN retraining_of_enrollment_id IS NOT NULL THEN 'Retraining' ELSE 'Initial' END"),
            dim("department",        "Department",        "user_department"),
            dim("attendance_marked", "Attendance Marked", "CASE WHEN attendance_marked THEN 'Yes' ELSE 'No' END"),
            dim("month",             "Month",             "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",              "Year",              "CAST(EXTRACT(YEAR FROM created_at) AS TEXT)"),
            met("user_name",         "User Name",         "user_name"),
            met("user_email",        "User Email",        "user_email"),
            met("enrolled_at",       "Enrolled At",       "created_at"),
            met("completed_at",      "Completed At",      "completed_at"),
            met("last_score",        "Score",             "last_score"),
            met("due_date",          "Due Date",          "due_date"),
            met("attempts_used",     "Attempts Used",     "attempts_used"),
            met("assigned_by",       "Assigned By",       "assigned_by_name"),
            met("overdue",           "Overdue?",          "CASE WHEN due_date < NOW() AND status NOT IN ('COMPLETED','WAIVED','CANCELLED') THEN 'Yes' ELSE 'No' END")
        ));

        // ── USER ──────────────────────────────────────────────────
        TABLE_MAP.put(ReportModule.USER, "users");
        FIELD_MAP.put(ReportModule.USER, List.of(
            // Dimensions (group-by fields)
            dim("department",            "Department",          "department"),
            dim("designation",           "Designation",         "designation"),
            dim("is_active",             "Account Status",      "CASE WHEN is_active THEN 'Active' ELSE 'Inactive' END"),
            dim("is_email_verified",     "Email Verified",      "CASE WHEN is_email_verified THEN 'Yes' ELSE 'No' END"),
            dim("must_change_password",  "Must Change Pwd",     "CASE WHEN must_change_password THEN 'Yes' ELSE 'No' END"),
            dim("is_dept_reviewer",      "Dept Reviewer",       "CASE WHEN is_dept_reviewer THEN 'Yes' ELSE 'No' END"),  // V18
            dim("is_qa_reviewer",        "QA Reviewer",         "CASE WHEN is_qa_reviewer THEN 'Yes' ELSE 'No' END"),    // V18
            dim("locked",                "Currently Locked",    "CASE WHEN locked_until IS NOT NULL AND locked_until > NOW() THEN 'Yes' ELSE 'No' END"),
            dim("month",                 "Month Joined",        "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",                  "Year Joined",         "CAST(EXTRACT(YEAR FROM created_at) AS TEXT)"),
            // Metrics (detail fields)
            met("username",              "Username",            "username"),
            met("email",                 "Email",               "email"),
            met("first_name",            "First Name",          "first_name"),
            met("last_name",             "Last Name",           "last_name"),
            met("full_name",             "Full Name",           "TRIM(CONCAT(first_name, ' ', COALESCE(last_name,'')))"),
            met("employee_id",           "Employee ID",         "employee_id"),
            met("phone",                 "Phone",               "phone"),
            met("department_id",         "Department ID",       "department_id"),  // V18
            met("joining_date",          "Joining Date",        "joining_date"),
            met("last_login_at",         "Last Login",          "last_login_at"),
            met("failed_login_attempts", "Failed Logins",       "failed_login_attempts"),
            met("locked_until",          "Locked Until",        "locked_until"),
            met("password_changed_at",   "Pwd Last Changed",    "password_changed_at"),
            met("created_at",            "Joined On",           "created_at")
        ));

        // ── DEPARTMENT ────────────────────────────────────────────
        // The org tree itself — useful for HR + compliance reports.
        // Joins are done as scalar subqueries to keep the existing
        // single-table query builder unchanged.
        TABLE_MAP.put(ReportModule.DEPARTMENT, "departments");
        FIELD_MAP.put(ReportModule.DEPARTMENT, List.of(
            // Dimensions
            dim("dept_type",   "Department Type", "dept_type"),
            dim("is_active",   "Active",          "CASE WHEN is_active THEN 'Yes' ELSE 'No' END"),
            dim("has_hod",     "Has HOD",         "CASE WHEN hod_user_id IS NOT NULL THEN 'Yes' ELSE 'No' END"),
            dim("month",       "Month Created",   "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",        "Year Created",    "CAST(EXTRACT(YEAR FROM created_at) AS TEXT)"),
            // Metrics
            met("name",        "Name",            "name"),
            met("code",        "Code",            "code"),
            met("description", "Description",     "description"),
            met("parent_name", "Parent Dept",
                    "(SELECT p.name FROM departments p WHERE p.id = departments.parent_id)"),
            met("hod_username","HOD Username",
                    "(SELECT u.username FROM users u WHERE u.id = departments.hod_user_id)"),
            met("hod_name",    "HOD Name",
                    "(SELECT TRIM(CONCAT(u.first_name,' ',COALESCE(u.last_name,''))) FROM users u WHERE u.id = departments.hod_user_id)"),
            met("member_count","Members",
                    "(SELECT COUNT(*) FROM users u WHERE u.department_id = departments.id AND u.is_deleted = FALSE)"),
            met("site_id",     "Site ID",         "site_id"),
            met("created_at",  "Created On",      "created_at")
        ));
    }

    private static FieldDef dim(String key, String label, String sql) {
        return new FieldDef(key, label, sql, true);
    }
    private static FieldDef met(String key, String label, String sql) {
        return new FieldDef(key, label, sql, false);
    }

    // ── Public API ─────────────────────────────────────────────

    public static String tableFor(ReportModule module) {
        return TABLE_MAP.get(module);
    }

    public static List<FieldDef> fieldsFor(ReportModule module) {
        return FIELD_MAP.getOrDefault(module, Collections.emptyList());
    }

    public static List<FieldDef> dimensionsFor(ReportModule module) {
        return fieldsFor(module).stream().filter(FieldDef::isDimension).toList();
    }

    public static List<FieldDef> metricsFor(ReportModule module) {
        return fieldsFor(module).stream().filter(f -> !f.isDimension()).toList();
    }

    public static FieldDef findField(ReportModule module, String key) {
        return fieldsFor(module).stream()
                .filter(f -> f.getKey().equals(key))
                .findFirst()
                .orElseThrow(() -> AppException.badRequest("Unknown field '" + key + "' for module " + module));
    }

    public static void validateFields(ReportModule module, List<String> dimensionKeys, List<String> metricKeys) {
        if (dimensionKeys != null) dimensionKeys.forEach(k -> findField(module, k));
        if (metricKeys    != null) metricKeys.forEach(k -> findField(module, k));
    }
}
