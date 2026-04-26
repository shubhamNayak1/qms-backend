package com.qms.module.reports.aggregation;

import com.qms.module.reports.dto.request.ReportFilter;
import com.qms.module.reports.dto.response.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Executes all native SQL aggregation queries against the existing QMS module tables.
 *
 * These queries run AGAINST the tables created by the other QMS modules
 * (qms_capa, qms_deviation, qms_incident, qms_change_control, qms_market_complaint).
 * No additional schema is required by the Reports module itself.
 *
 * Pattern: each method builds a parameterised native query from the ReportFilter,
 * executes it, and maps Object[] rows to typed DTOs.
 *
 * All methods are read-only — they run in a single transaction for a consistent snapshot.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ReportQueryService {

    @PersistenceContext
    private EntityManager em;

    // ═══════════════════════════════════════════════════════════
    //  CAPA AGGREGATIONS
    // ═══════════════════════════════════════════════════════════

    public List<AggregationResult> capaByStatus(ReportFilter f) {
        return aggregateByGroup("qms_capa", "status", f);
    }

    public List<AggregationResult> capaByPriority(ReportFilter f) {
        return aggregateByGroup("qms_capa", "priority", f);
    }

    public List<AggregationResult> capaByDepartment(ReportFilter f) {
        return aggregateByGroup("qms_capa", "department", f);
    }

    public List<AggregationResult> capaBySource(ReportFilter f) {
        return aggregateByGroup("qms_capa", "source", f);
    }

    public List<AggregationResult> capaMonthlyTrend(ReportFilter f) {
        return monthlyTrend("qms_capa", f);
    }

    public List<AggregationResult> capaAvgResolutionByPriority(ReportFilter f) {
        return avgResolutionDaysByGroup("qms_capa", "priority", f);
    }

    public ReportSummary capaSummary(ReportFilter f) {
        return buildSummary("CAPA", "qms_capa", f);
    }

    // ═══════════════════════════════════════════════════════════
    //  DEVIATION AGGREGATIONS
    // ═══════════════════════════════════════════════════════════

    public List<AggregationResult> deviationByStatus(ReportFilter f) {
        return aggregateByGroup("qms_deviation", "status", f);
    }

    public List<AggregationResult> deviationByPriority(ReportFilter f) {
        return aggregateByGroup("qms_deviation", "priority", f);
    }

    public List<AggregationResult> deviationByDepartment(ReportFilter f) {
        return aggregateByGroup("qms_deviation", "department", f);
    }

    public List<AggregationResult> deviationByType(ReportFilter f) {
        return aggregateByGroup("qms_deviation", "deviation_type", f);
    }

    public List<AggregationResult> deviationByProcessArea(ReportFilter f) {
        return aggregateByGroup("qms_deviation", "process_area", f);
    }

    public List<AggregationResult> deviationMonthlyTrend(ReportFilter f) {
        return monthlyTrend("qms_deviation", f);
    }

    public ReportSummary deviationSummary(ReportFilter f) {
        return buildSummary("DEVIATION", "qms_deviation", f);
    }

    // ═══════════════════════════════════════════════════════════
    //  INCIDENT AGGREGATIONS
    // ═══════════════════════════════════════════════════════════

    public List<AggregationResult> incidentByStatus(ReportFilter f) {
        return aggregateByGroup("qms_incident", "status", f);
    }

    public List<AggregationResult> incidentBySeverity(ReportFilter f) {
        return aggregateByGroup("qms_incident", "severity", f);
    }

    public List<AggregationResult> incidentByDepartment(ReportFilter f) {
        return aggregateByGroup("qms_incident", "department", f);
    }

    public List<AggregationResult> incidentByType(ReportFilter f) {
        return aggregateByGroup("qms_incident", "incident_type", f);
    }

    public List<AggregationResult> incidentByLocation(ReportFilter f) {
        return aggregateByGroup("qms_incident", "location", f);
    }

    public List<AggregationResult> incidentMonthlyTrend(ReportFilter f) {
        return monthlyTrend("qms_incident", f);
    }

    public ReportSummary incidentSummary(ReportFilter f) {
        return buildSummary("INCIDENT", "qms_incident", f);
    }

    // ═══════════════════════════════════════════════════════════
    //  USER AGGREGATIONS (against users table)
    // ═══════════════════════════════════════════════════════════

    public List<AggregationResult> usersByDepartment() {
        String sql = """
                SELECT department, COUNT(*) AS cnt
                FROM users
                WHERE is_deleted = FALSE
                GROUP BY department
                ORDER BY cnt DESC
                """;
        return mapToAggregation(em.createNativeQuery(sql).getResultList());
    }

    public List<AggregationResult> usersByRole() {
        String sql = """
                SELECT r.name AS role_name, COUNT(DISTINCT ur.user_id) AS cnt
                FROM roles r
                JOIN user_roles ur ON ur.role_id = r.id
                JOIN users u ON u.id = ur.user_id AND u.is_deleted = FALSE
                GROUP BY r.name
                ORDER BY cnt DESC
                """;
        return mapToAggregation(em.createNativeQuery(sql).getResultList());
    }

    public List<AggregationResult> userActivityTrend(ReportFilter f) {
        // Count logins per month from audit_logs
        String sql = """
                SELECT DATE_FORMAT(timestamp, '%Y-%m') AS month,
                       COUNT(*) AS cnt
                FROM audit_logs
                WHERE action = 'LOGIN'
                  AND outcome = 'SUCCESS'
                  """ + dateRangeClause(f, "timestamp") + """
                GROUP BY month
                ORDER BY month ASC
                """;
        return mapToAggregation(em.createNativeQuery(sql).getResultList());
    }

    // ═══════════════════════════════════════════════════════════
    //  UNIFIED DASHBOARD
    // ═══════════════════════════════════════════════════════════

    public QmsDashboardResponse buildDashboard() {
        LocalDate today = LocalDate.now();

        return QmsDashboardResponse.builder()
                .generatedAt(LocalDateTime.now())

                // CAPA
                .capaTotal(countTable("qms_capa", null))
                .capaOpen(countByStatus("qms_capa", "DRAFT"))
                .capaInProgress(countByStatus("qms_capa", "PENDING_HOD")
                        + countByStatus("qms_capa", "PENDING_QA_REVIEW")
                        + countByStatus("qms_capa", "PENDING_DEPT_COMMENT"))
                .capaPendingApproval(countByStatus("qms_capa", "PENDING_HEAD_QA"))
                .capaClosed(countByStatus("qms_capa", "CLOSED"))
                .capaOverdue(countOverdue("qms_capa", today))
                .capaCritical(countByPriority("qms_capa", "CRITICAL"))

                // Deviation
                .deviationTotal(countTable("qms_deviation", null))
                .deviationOpen(countByStatus("qms_deviation", "DRAFT"))
                .deviationInProgress(countByStatus("qms_deviation", "PENDING_HOD")
                        + countByStatus("qms_deviation", "PENDING_QA_REVIEW")
                        + countByStatus("qms_deviation", "PENDING_RA_REVIEW")
                        + countByStatus("qms_deviation", "PENDING_INVESTIGATION")
                        + countByStatus("qms_deviation", "PENDING_VERIFICATION"))
                .deviationClosed(countByStatus("qms_deviation", "CLOSED"))
                .deviationOverdue(countOverdue("qms_deviation", today))
                .deviationRegulatoryReportable(countFlag("qms_deviation", "regulatory_reportable"))

                // Incident
                .incidentTotal(countTable("qms_incident", null))
                .incidentOpen(countByStatus("qms_incident", "DRAFT"))
                .incidentInProgress(countByStatus("qms_incident", "PENDING_HOD")
                        + countByStatus("qms_incident", "PENDING_INVESTIGATION")
                        + countByStatus("qms_incident", "PENDING_ATTACHMENTS")
                        + countByStatus("qms_incident", "PENDING_VERIFICATION")
                        + countByStatus("qms_incident", "PENDING_HEAD_QA"))
                .incidentClosed(countByStatus("qms_incident", "CLOSED"))
                .incidentOverdue(countOverdue("qms_incident", today))
                .incidentCriticalSeverity(countBySingleField("qms_incident", "severity", "Critical"))
                .incidentInjuryInvolved(countFlag("qms_incident", "injury_involved"))

                // Change Control
                .changeControlTotal(countTable("qms_change_control", null))
                .changeControlOpen(countByStatus("qms_change_control", "DRAFT"))
                .changeControlPendingApproval(countByStatus("qms_change_control", "PENDING_HEAD_QA")
                        + countByStatus("qms_change_control", "PENDING_VERIFICATION"))
                .changeControlClosed(countByStatus("qms_change_control", "CLOSED"))
                .changeControlOverdue(countOverdue("qms_change_control", today))

                // Complaint
                .complaintTotal(countTable("qms_market_complaint", null))
                .complaintOpen(countByStatus("qms_market_complaint", "DRAFT"))
                .complaintInProgress(countByStatus("qms_market_complaint", "PENDING_HOD")
                        + countByStatus("qms_market_complaint", "PENDING_INVESTIGATION")
                        + countByStatus("qms_market_complaint", "PENDING_ATTACHMENTS")
                        + countByStatus("qms_market_complaint", "PENDING_VERIFICATION"))
                .complaintClosed(countByStatus("qms_market_complaint", "CLOSED"))
                .complaintOverdue(countOverdue("qms_market_complaint", today))
                .complaintReportableToAuthority(countFlag("qms_market_complaint", "reportable_to_authority"))

                // Cross-module aggregates
                .totalOpenRecords(
                        countByStatus("qms_capa", "DRAFT") +
                        countByStatus("qms_deviation", "DRAFT") +
                        countByStatus("qms_incident", "DRAFT") +
                        countByStatus("qms_change_control", "DRAFT") +
                        countByStatus("qms_market_complaint", "DRAFT"))
                .totalOverdueRecords(
                        countOverdue("qms_capa", today) +
                        countOverdue("qms_deviation", today) +
                        countOverdue("qms_incident", today) +
                        countOverdue("qms_change_control", today) +
                        countOverdue("qms_market_complaint", today))

                // Trend & breakdown (CAPA-focused as the primary QMS module)
                .monthlyOpenTrend(combinedMonthlyTrend("DRAFT"))
                .openByDepartment(combinedOpenByDepartment())
                .avgCapaResolutionDays(avgResolutionDays("qms_capa"))
                .avgDeviationResolutionDays(avgResolutionDays("qms_deviation"))
                .avgIncidentResolutionDays(avgResolutionDays("qms_incident"))
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    //  FLAT DATA QUERIES (for table/export)
    // ═══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public List<CapaReportRow> capaRows(ReportFilter f) {
        String sql = "SELECT id, record_number, title, status, priority, source, capa_type, "
                + "department, assigned_to_name, raised_by_name, approved_by_name, "
                + "due_date, closed_date, effectiveness_check_date, is_effective, "
                + "linked_deviation_number, root_cause, corrective_action, created_at, updated_at, created_by "
                + "FROM qms_capa WHERE is_deleted = FALSE"
                + buildWhereClause(f, "qms_capa")
                + buildOrderClause(f);

        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        return rows.stream().map(r -> CapaReportRow.builder()
                .id(toLong(r[0])).recordNumber(str(r[1])).title(str(r[2]))
                .status(str(r[3])).priority(str(r[4])).source(str(r[5])).capaType(str(r[6]))
                .department(str(r[7])).assignedToName(str(r[8])).raisedByName(str(r[9]))
                .approvedByName(str(r[10])).dueDate(toDate(r[11])).closedDate(toDate(r[12]))
                .effectivenessCheckDate(toDate(r[13])).isEffective(toBool(r[14]))
                .linkedDeviationNumber(str(r[15])).rootCause(str(r[16])).correctiveAction(str(r[17]))
                .createdAt(toDateTime(r[18])).updatedAt(toDateTime(r[19])).createdBy(str(r[20]))
                .overdue(isOverdue(toDate(r[11]), str(r[3])))
                .ageInDays(ageDays(toDateTime(r[18])))
                .build()).toList();
    }

    @SuppressWarnings("unchecked")
    public List<DeviationReportRow> deviationRows(ReportFilter f) {
        String sql = "SELECT id, record_number, title, status, priority, deviation_type, product_batch, "
                + "process_area, department, assigned_to_name, raised_by_name, capa_required, "
                + "capa_reference, regulatory_reportable, impact_assessment, due_date, closed_date, created_at, created_by "
                + "FROM qms_deviation WHERE is_deleted = FALSE"
                + buildWhereClause(f, "qms_deviation")
                + buildOrderClause(f);

        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        return rows.stream().map(r -> DeviationReportRow.builder()
                .id(toLong(r[0])).recordNumber(str(r[1])).title(str(r[2]))
                .status(str(r[3])).priority(str(r[4])).deviationType(str(r[5]))
                .productBatch(str(r[6])).processArea(str(r[7])).department(str(r[8]))
                .assignedToName(str(r[9])).raisedByName(str(r[10]))
                .capaRequired(toBool(r[11])).capaReference(str(r[12]))
                .regulatoryReportable(toBool(r[13])).impactAssessment(str(r[14]))
                .dueDate(toDate(r[15])).closedDate(toDate(r[16]))
                .createdAt(toDateTime(r[17])).createdBy(str(r[18]))
                .overdue(isOverdue(toDate(r[15]), str(r[3])))
                .ageInDays(ageDays(toDateTime(r[17])))
                .build()).toList();
    }

    @SuppressWarnings("unchecked")
    public List<IncidentReportRow> incidentRows(ReportFilter f) {
        String sql = "SELECT id, record_number, title, status, priority, incident_type, severity, "
                + "location, department, occurrence_date, reported_by, assigned_to_name, "
                + "injury_involved, capa_reference, due_date, closed_date, created_at, created_by "
                + "FROM qms_incident WHERE is_deleted = FALSE"
                + buildWhereClause(f, "qms_incident")
                + buildOrderClause(f);

        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        return rows.stream().map(r -> IncidentReportRow.builder()
                .id(toLong(r[0])).recordNumber(str(r[1])).title(str(r[2]))
                .status(str(r[3])).priority(str(r[4])).incidentType(str(r[5])).severity(str(r[6]))
                .location(str(r[7])).department(str(r[8])).occurrenceDate(toDate(r[9]))
                .reportedBy(str(r[10])).assignedToName(str(r[11]))
                .injuryInvolved(toBool(r[12])).capaReference(str(r[13]))
                .dueDate(toDate(r[14])).closedDate(toDate(r[15]))
                .createdAt(toDateTime(r[16])).createdBy(str(r[17]))
                .overdue(isOverdue(toDate(r[14]), str(r[3])))
                .ageInDays(ageDays(toDateTime(r[16])))
                .build()).toList();
    }

    // ═══════════════════════════════════════════════════════════
    //  SHARED QUERY BUILDERS
    // ═══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private List<AggregationResult> aggregateByGroup(String table, String groupCol, ReportFilter f) {
        String sql = "SELECT " + groupCol + ", COUNT(*) AS cnt "
                + "FROM " + table + " WHERE is_deleted = FALSE"
                + buildWhereClause(f, table)
                + " GROUP BY " + groupCol + " ORDER BY cnt DESC";
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        long total = rows.stream().mapToLong(r -> toLong(r[1])).sum();
        return rows.stream()
                .map(r -> {
                    long count = toLong(r[1]);
                    double pct = total == 0 ? 0.0 : count * 100.0 / total;
                    return AggregationResult.of(str(r[0]), count, pct);
                }).toList();
    }

    @SuppressWarnings("unchecked")
    private List<AggregationResult> monthlyTrend(String table, ReportFilter f) {
        String sql = "SELECT DATE_FORMAT(created_at, '%Y-%m') AS month, COUNT(*) AS cnt "
                + "FROM " + table + " WHERE is_deleted = FALSE"
                + dateRangeClause(f, "created_at")
                + " GROUP BY month ORDER BY month ASC";
        return mapToAggregation(em.createNativeQuery(sql).getResultList());
    }

    @SuppressWarnings("unchecked")
    private List<AggregationResult> avgResolutionDaysByGroup(String table, String groupCol, ReportFilter f) {
        String sql = "SELECT " + groupCol + ", "
                + "AVG(DATEDIFF(COALESCE(closed_date, CURDATE()), DATE(created_at))) AS avg_days, "
                + "COUNT(*) AS cnt "
                + "FROM " + table + " WHERE is_deleted = FALSE"
                + buildWhereClause(f, table)
                + " GROUP BY " + groupCol;
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        return rows.stream()
                .map(r -> AggregationResult.builder()
                        .label(str(r[0])).avgDays(toDouble(r[1])).count(toLong(r[2])).build())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private ReportSummary buildSummary(String type, String table, ReportFilter f) {
        String baseWhere = " WHERE is_deleted = FALSE" + buildWhereClause(f, table);
        long total       = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table + baseWhere)
                                       .getSingleResult()).longValue();
        long open        = countByStatusFiltered(table, "DRAFT",       f);
        long inProgress  = countByStatusFiltered(table, "PENDING_HOD", f);
        long closed      = countByStatusFiltered(table, "CLOSED",      f);
        long overdue     = countOverdue(table, LocalDate.now());
        long critical    = countByPriorityFiltered(table, "CRITICAL",  f);

        Number avgRes = (Number) em.createNativeQuery(
                "SELECT AVG(DATEDIFF(COALESCE(closed_date, CURDATE()), DATE(created_at))) "
                + "FROM " + table + baseWhere + " AND closed_date IS NOT NULL")
                .getSingleResult();
        double avgDays = avgRes != null ? Math.round(avgRes.doubleValue() * 10) / 10.0 : 0.0;

        return ReportSummary.builder()
                .reportType(type)
                .generatedAt(LocalDateTime.now())
                .periodFrom(f.getDateFrom()).periodTo(f.getDateTo())
                .totalRecords(total).openCount(open).inProgressCount(inProgress)
                .closedCount(closed).overdueCount(overdue).criticalCount(critical)
                .avgResolutionDays(avgDays)
                .overdueRate(total == 0 ? 0.0 : Math.round(overdue * 10000.0 / total) / 100.0)
                .byStatus(aggregateByGroup(table, "status",     f))
                .byPriority(aggregateByGroup(table, "priority", f))
                .byDepartment(aggregateByGroup(table, "department", f))
                .monthlyTrend(monthlyTrend(table, f))
                .build();
    }

    // ── Count helpers ─────────────────────────────────────────

    private long countTable(String table, ReportFilter f) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE is_deleted = FALSE"
                + (f != null ? buildWhereClause(f, table) : "");
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private long countByStatus(String table, String status) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE is_deleted = FALSE AND status = '" + status + "'")
                .getSingleResult()).longValue();
    }

    private long countByStatusFiltered(String table, String status, ReportFilter f) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE is_deleted = FALSE AND status = '" + status + "'"
                + buildWhereClause(f, table)).getSingleResult()).longValue();
    }

    private long countByPriority(String table, String priority) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE is_deleted = FALSE AND priority = '" + priority + "'")
                .getSingleResult()).longValue();
    }

    private long countByPriorityFiltered(String table, String priority, ReportFilter f) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE is_deleted = FALSE AND priority = '" + priority + "'"
                + buildWhereClause(f, table)).getSingleResult()).longValue();
    }

    private long countBySingleField(String table, String col, String val) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE is_deleted = FALSE AND " + col + " = '" + val + "'")
                .getSingleResult()).longValue();
    }

    private long countFlag(String table, String col) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE is_deleted = FALSE AND " + col + " = TRUE")
                .getSingleResult()).longValue();
    }

    private long countOverdue(String table, LocalDate today) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM " + table
                + " WHERE is_deleted = FALSE AND due_date < '" + today
                + "' AND status NOT IN ('CLOSED','CANCELLED')")
                .getSingleResult()).longValue();
    }

    private double avgResolutionDays(String table) {
        Number result = (Number) em.createNativeQuery(
                "SELECT AVG(DATEDIFF(closed_date, DATE(created_at))) FROM " + table
                + " WHERE is_deleted = FALSE AND closed_date IS NOT NULL").getSingleResult();
        return result != null ? Math.round(result.doubleValue() * 10) / 10.0 : 0.0;
    }

    // ── Combined dashboard queries ─────────────────────────────

    @SuppressWarnings("unchecked")
    private List<AggregationResult> combinedMonthlyTrend(String status) {
        String sql = """
                SELECT month, SUM(cnt) AS total FROM (
                  SELECT DATE_FORMAT(created_at,'%Y-%m') month, COUNT(*) cnt FROM qms_capa
                    WHERE is_deleted=FALSE AND status=? GROUP BY month
                  UNION ALL
                  SELECT DATE_FORMAT(created_at,'%Y-%m'), COUNT(*) FROM qms_deviation
                    WHERE is_deleted=FALSE AND status=? GROUP BY 1
                  UNION ALL
                  SELECT DATE_FORMAT(created_at,'%Y-%m'), COUNT(*) FROM qms_incident
                    WHERE is_deleted=FALSE AND status=? GROUP BY 1
                ) t GROUP BY month ORDER BY month DESC LIMIT 6
                """;
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter(1, status).setParameter(2, status).setParameter(3, status)
                .getResultList();
        return mapToAggregation(rows);
    }

    @SuppressWarnings("unchecked")
    private List<AggregationResult> combinedOpenByDepartment() {
        String sql = """
                SELECT department, SUM(cnt) AS total FROM (
                  SELECT department, COUNT(*) cnt FROM qms_capa WHERE is_deleted=FALSE AND status='OPEN' GROUP BY department
                  UNION ALL
                  SELECT department, COUNT(*) FROM qms_deviation WHERE is_deleted=FALSE AND status='OPEN' GROUP BY department
                  UNION ALL
                  SELECT department, COUNT(*) FROM qms_incident WHERE is_deleted=FALSE AND status='OPEN' GROUP BY department
                ) t GROUP BY department ORDER BY total DESC LIMIT 10
                """;
        return mapToAggregation(em.createNativeQuery(sql).getResultList());
    }

    // ── WHERE clause builders ─────────────────────────────────

    private String buildWhereClause(ReportFilter f, String table) {
        if (f == null) return "";
        StringBuilder sb = new StringBuilder();
        if (f.getDateFrom() != null)
            sb.append(" AND DATE(created_at) >= '").append(f.getDateFrom()).append("'");
        if (f.getDateTo() != null)
            sb.append(" AND DATE(created_at) <= '").append(f.getDateTo()).append("'");
        if (f.getStatuses() != null && !f.getStatuses().isEmpty())
            sb.append(" AND status IN (")
              .append(inList(f.getStatuses())).append(")");
        if (f.getPriorities() != null && !f.getPriorities().isEmpty())
            sb.append(" AND priority IN (")
              .append(inList(f.getPriorities())).append(")");
        if (f.getDepartment() != null)
            sb.append(" AND department = '").append(escape(f.getDepartment())).append("'");
        if (f.getAssignedToId() != null)
            sb.append(" AND assigned_to_id = ").append(f.getAssignedToId());
        if (Boolean.TRUE.equals(f.getOverdueOnly()))
            sb.append(" AND due_date < CURDATE() AND status NOT IN ('CLOSED','CANCELLED')");
        if (f.getSearch() != null && !f.getSearch().isBlank()) {
            String s = escape(f.getSearch());
            sb.append(" AND (title LIKE '%").append(s).append("%'")
              .append(" OR record_number LIKE '%").append(s).append("%')");
        }
        // Module-specific additions
        if ("qms_capa".equals(table)) {
            if (f.getSource()   != null) sb.append(" AND source = '").append(escape(f.getSource())).append("'");
            if (f.getCapaType() != null) sb.append(" AND capa_type = '").append(escape(f.getCapaType())).append("'");
        }
        if ("qms_deviation".equals(table)) {
            if (f.getDeviationType() != null) sb.append(" AND deviation_type = '").append(escape(f.getDeviationType())).append("'");
            if (f.getProcessArea()   != null) sb.append(" AND process_area = '").append(escape(f.getProcessArea())).append("'");
            if (Boolean.TRUE.equals(f.getRegulatoryReportable())) sb.append(" AND regulatory_reportable = TRUE");
        }
        if ("qms_incident".equals(table)) {
            if (f.getSeverity()     != null) sb.append(" AND severity = '").append(escape(f.getSeverity())).append("'");
            if (f.getIncidentType() != null) sb.append(" AND incident_type = '").append(escape(f.getIncidentType())).append("'");
            if (Boolean.TRUE.equals(f.getInjuryInvolved())) sb.append(" AND injury_involved = TRUE");
        }
        if ("qms_change_control".equals(table)) {
            if (f.getChangeType() != null) sb.append(" AND change_type = '").append(escape(f.getChangeType())).append("'");
            if (f.getRiskLevel()  != null) sb.append(" AND risk_level = '").append(escape(f.getRiskLevel())).append("'");
        }
        if ("qms_market_complaint".equals(table)) {
            if (f.getComplaintCategory() != null) sb.append(" AND complaint_category = '").append(escape(f.getComplaintCategory())).append("'");
            if (f.getCustomerCountry()   != null) sb.append(" AND customer_country = '").append(escape(f.getCustomerCountry())).append("'");
            if (Boolean.TRUE.equals(f.getReportableToAuthority())) sb.append(" AND reportable_to_authority = TRUE");
        }
        return sb.toString();
    }

    private String dateRangeClause(ReportFilter f, String col) {
        if (f == null) return "";
        StringBuilder sb = new StringBuilder();
        if (f.getDateFrom() != null) sb.append(" AND DATE(").append(col).append(") >= '").append(f.getDateFrom()).append("'");
        if (f.getDateTo()   != null) sb.append(" AND DATE(").append(col).append(") <= '").append(f.getDateTo()).append("'");
        return sb.toString();
    }

    private String buildOrderClause(ReportFilter f) {
        String col = "created_at";
        if (f != null && f.getSortBy() != null && f.getSortBy().matches("[a-zA-Z_]+")) {
            col = camelToSnake(f.getSortBy());
        }
        String dir = (f != null && "ASC".equalsIgnoreCase(f.getSortDir())) ? "ASC" : "DESC";
        return " ORDER BY " + col + " " + dir;
    }

    // ── Type conversion helpers ───────────────────────────────

    @SuppressWarnings("unchecked")
    private List<AggregationResult> mapToAggregation(List<Object[]> rows) {
        long total = rows.stream().mapToLong(r -> toLong(r[1])).sum();
        return rows.stream()
                .map(r -> {
                    long count = toLong(r[1]);
                    double pct = total == 0 ? 0.0 : count * 100.0 / total;
                    return AggregationResult.of(str(r[0]), count, pct);
                }).toList();
    }

    private String str(Object o)               { return o != null ? o.toString() : null; }
    private Long   toLong(Object o)            { return o instanceof Number n ? n.longValue() : 0L; }
    private Double toDouble(Object o)          { return o instanceof Number n ? Math.round(n.doubleValue() * 10) / 10.0 : null; }
    private Boolean toBool(Object o)           { return o instanceof Number n ? n.intValue() == 1 : o instanceof Boolean b ? b : null; }

    private LocalDate     toDate(Object o)     { return o instanceof java.sql.Date d ? d.toLocalDate() : null; }
    private LocalDateTime toDateTime(Object o) {
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (o instanceof LocalDateTime dt)     return dt;
        return null;
    }

    private boolean isOverdue(LocalDate due, String status) {
        if (due == null) return false;
        if ("CLOSED".equals(status) || "CANCELLED".equals(status)) return false;
        return LocalDate.now().isAfter(due);
    }

    private long ageDays(LocalDateTime createdAt) {
        if (createdAt == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDate.now());
    }

    private String inList(List<String> items) {
        return items.stream()
                .map(s -> "'" + escape(s) + "'")
                .reduce((a, b) -> a + "," + b).orElse("''");
    }

    private String escape(String s) {
        // Basic SQL injection mitigation — restrict to alphanumeric, space, hyphen, underscore
        return s == null ? "" : s.replaceAll("[^a-zA-Z0-9 \\-_\\.@]", "");
    }

    private String camelToSnake(String s) {
        return s.replaceAll("([A-Z])", "_$1").toLowerCase();
    }
}
