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
            dim("status",      "Status",      "status"),
            dim("priority",    "Priority",    "priority"),
            dim("source",      "Source",      "source"),
            dim("capa_type",   "CAPA Type",   "capa_type"),
            dim("department",  "Department",  "department"),
            dim("month",       "Month",       "TO_CHAR(created_at,'YYYY-MM')"),
            dim("quarter",     "Quarter",     "CONCAT(EXTRACT(YEAR FROM created_at),'-Q',CEIL(EXTRACT(MONTH FROM created_at)/3.0)::INT)"),
            dim("year",        "Year",        "EXTRACT(YEAR FROM created_at)::TEXT"),
            // Metrics
            met("record_number","Record #",   "record_number"),
            met("title",        "Title",      "title"),
            met("assigned_to",  "Assigned To","assigned_to_name"),
            met("raised_by",    "Raised By",  "raised_by_name"),
            met("due_date",     "Due Date",   "due_date"),
            met("closed_date",  "Closed Date","closed_date"),
            met("age_days",     "Age (Days)", "COALESCE(EXTRACT(DAY FROM NOW()-created_at)::INT,0)"),
            met("is_effective", "Effective?", "CASE WHEN is_effective THEN 'Yes' WHEN is_effective=false THEN 'No' ELSE '' END"),
            met("overdue",      "Overdue?",   "CASE WHEN due_date < NOW() AND status NOT IN ('CLOSED','CANCELLED') THEN 'Yes' ELSE 'No' END")
        ));

        // ── DEVIATION ─────────────────────────────────────────
        TABLE_MAP.put(ReportModule.DEVIATION, "qms_deviation");
        FIELD_MAP.put(ReportModule.DEVIATION, List.of(
            dim("status",              "Status",             "status"),
            dim("priority",            "Priority",           "priority"),
            dim("deviation_type",      "Deviation Type",     "deviation_type"),
            dim("process_area",        "Process Area",       "process_area"),
            dim("department",          "Department",         "department"),
            dim("regulatory_reportable","Regulatory Reportable","CASE WHEN regulatory_reportable THEN 'Yes' ELSE 'No' END"),
            dim("month",               "Month",              "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",                "Year",               "EXTRACT(YEAR FROM created_at)::TEXT"),
            met("record_number",       "Record #",           "record_number"),
            met("title",               "Title",              "title"),
            met("assigned_to",         "Assigned To",        "assigned_to_name"),
            met("raised_by",           "Raised By",          "raised_by_name"),
            met("due_date",            "Due Date",           "due_date"),
            met("closed_date",         "Closed Date",        "closed_date"),
            met("capa_required",       "CAPA Required?",     "CASE WHEN capa_required THEN 'Yes' ELSE 'No' END"),
            met("age_days",            "Age (Days)",         "COALESCE(EXTRACT(DAY FROM NOW()-created_at)::INT,0)"),
            met("overdue",             "Overdue?",           "CASE WHEN due_date < NOW() AND status NOT IN ('CLOSED','CANCELLED') THEN 'Yes' ELSE 'No' END")
        ));

        // ── INCIDENT ──────────────────────────────────────────
        TABLE_MAP.put(ReportModule.INCIDENT, "qms_incident");
        FIELD_MAP.put(ReportModule.INCIDENT, List.of(
            dim("status",        "Status",        "status"),
            dim("severity",      "Severity",      "severity"),
            dim("incident_type", "Incident Type", "incident_type"),
            dim("location",      "Location",      "location"),
            dim("department",    "Department",    "department"),
            dim("injury_involved","Injury Involved","CASE WHEN injury_involved THEN 'Yes' ELSE 'No' END"),
            dim("month",         "Month",         "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",          "Year",          "EXTRACT(YEAR FROM created_at)::TEXT"),
            met("record_number", "Record #",      "record_number"),
            met("title",         "Title",         "title"),
            met("assigned_to",   "Assigned To",   "assigned_to_name"),
            met("occurrence_date","Occurrence Date","occurrence_date"),
            met("due_date",      "Due Date",      "due_date"),
            met("closed_date",   "Closed Date",   "closed_date"),
            met("age_days",      "Age (Days)",    "COALESCE(EXTRACT(DAY FROM NOW()-created_at)::INT,0)"),
            met("overdue",       "Overdue?",      "CASE WHEN due_date < NOW() AND status NOT IN ('CLOSED','CANCELLED') THEN 'Yes' ELSE 'No' END")
        ));

        // ── CHANGE_CONTROL ────────────────────────────────────
        TABLE_MAP.put(ReportModule.CHANGE_CONTROL, "qms_change_control");
        FIELD_MAP.put(ReportModule.CHANGE_CONTROL, List.of(
            dim("status",      "Status",      "status"),
            dim("priority",    "Priority",    "priority"),
            dim("change_type", "Change Type", "change_type"),
            dim("risk_level",  "Risk Level",  "risk_level"),
            dim("department",  "Department",  "department"),
            dim("month",       "Month",       "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",        "Year",        "EXTRACT(YEAR FROM created_at)::TEXT"),
            met("record_number","Record #",   "record_number"),
            met("title",        "Title",      "title"),
            met("requested_by", "Requested By","requested_by_name"),
            met("due_date",     "Due Date",   "due_date"),
            met("closed_date",  "Closed Date","closed_date"),
            met("age_days",     "Age (Days)", "COALESCE(EXTRACT(DAY FROM NOW()-created_at)::INT,0)"),
            met("overdue",      "Overdue?",   "CASE WHEN due_date < NOW() AND status NOT IN ('CLOSED','CANCELLED') THEN 'Yes' ELSE 'No' END")
        ));

        // ── COMPLAINT ─────────────────────────────────────────
        TABLE_MAP.put(ReportModule.COMPLAINT, "qms_market_complaint");
        FIELD_MAP.put(ReportModule.COMPLAINT, List.of(
            dim("status",                  "Status",                "status"),
            dim("priority",                "Priority",              "priority"),
            dim("complaint_category",      "Category",              "complaint_category"),
            dim("customer_country",        "Country",               "customer_country"),
            dim("department",              "Department",            "department"),
            dim("reportable_to_authority", "Reportable?",           "CASE WHEN reportable_to_authority THEN 'Yes' ELSE 'No' END"),
            dim("month",                   "Month",                 "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",                    "Year",                  "EXTRACT(YEAR FROM created_at)::TEXT"),
            met("record_number",           "Record #",              "record_number"),
            met("title",                   "Title",                 "title"),
            met("customer_name",           "Customer",              "customer_name"),
            met("assigned_to",             "Assigned To",           "assigned_to_name"),
            met("due_date",                "Due Date",              "due_date"),
            met("closed_date",             "Closed Date",           "closed_date"),
            met("age_days",                "Age (Days)",            "COALESCE(EXTRACT(DAY FROM NOW()-created_at)::INT,0)")
        ));

        // ── LMS_ENROLLMENT ────────────────────────────────────
        TABLE_MAP.put(ReportModule.LMS_ENROLLMENT, "lms_enrollments");
        FIELD_MAP.put(ReportModule.LMS_ENROLLMENT, List.of(
            dim("status",          "Enrollment Status", "status"),
            dim("training_type",   "Training Type",     "training_type"),
            dim("department",      "Department",        "user_department"),
            dim("month",           "Month",             "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",            "Year",              "EXTRACT(YEAR FROM created_at)::TEXT"),
            met("user_name",       "User Name",         "user_name"),
            met("user_email",      "User Email",        "user_email"),
            met("enrolled_at",     "Enrolled At",       "created_at"),
            met("completed_at",    "Completed At",      "completed_at"),
            met("last_score",      "Score",             "last_score"),
            met("due_date",        "Due Date",          "due_date"),
            met("attempts_used",   "Attempts Used",     "attempts_used"),
            met("overdue",         "Overdue?",          "CASE WHEN due_date < NOW() AND status NOT IN ('COMPLETED','WAIVED','CANCELLED') THEN 'Yes' ELSE 'No' END")
        ));

        // ── USER ──────────────────────────────────────────────────
        TABLE_MAP.put(ReportModule.USER, "users");
        FIELD_MAP.put(ReportModule.USER, List.of(
            // Dimensions (group-by fields)
            dim("department",          "Department",        "department"),
            dim("designation",         "Designation",       "designation"),
            dim("is_active",           "Account Status",    "CASE WHEN is_active THEN 'Active' ELSE 'Inactive' END"),
            dim("is_email_verified",   "Email Verified",    "CASE WHEN is_email_verified THEN 'Yes' ELSE 'No' END"),
            dim("must_change_password","Must Change Pwd",   "CASE WHEN must_change_password THEN 'Yes' ELSE 'No' END"),
            dim("month",               "Month Joined",      "TO_CHAR(created_at,'YYYY-MM')"),
            dim("year",                "Year Joined",       "EXTRACT(YEAR FROM created_at)::TEXT"),
            // Metrics (detail fields)
            met("username",            "Username",          "username"),
            met("email",               "Email",             "email"),
            met("first_name",          "First Name",        "first_name"),
            met("last_name",           "Last Name",         "last_name"),
            met("employee_id",         "Employee ID",       "employee_id"),
            met("phone",               "Phone",             "phone"),
            met("last_login_at",       "Last Login",        "last_login_at"),
            met("failed_login_attempts","Failed Logins",    "failed_login_attempts"),
            met("locked_until",        "Locked Until",      "locked_until"),
            met("password_changed_at", "Pwd Last Changed",  "password_changed_at"),
            met("created_at",          "Joined On",         "created_at")
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
