package com.qms.module.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComplianceDashboardResponse {

    private LocalDateTime generatedAt;

    // ── Enrollment KPIs ──────────────────────────────────────
    private long totalEnrollments;
    private long enrolledCount;
    private long inProgressCount;
    private long completedCount;
    private long failedCount;
    private long expiredCount;
    private long waivedCount;
    private long cancelledCount;
    private long overdueCount;

    /** Overall compliance rate across all non-cancelled enrollments (%). */
    private double overallComplianceRate;

    // ── Program KPIs ─────────────────────────────────────────
    private long totalPrograms;
    private long activePrograms;
    private long mandatoryPrograms;

    // ── Certificate KPIs ─────────────────────────────────────
    private long activeCertificates;
    private long expiredCertificates;

    // ── Attention lists ──────────────────────────────────────
    private List<EnrollmentResponse>  overdueEnrollments;
    private List<EnrollmentResponse>  dueSoonEnrollments;
    private List<CertificateResponse> expiringSoonCertificates;

    /** Compliance rate per department: { "Manufacturing": 87.5, "QA": 100.0 } */
    private Map<String, Double> complianceByDepartment;

    /** Top 5 programs with lowest compliance rate. */
    private List<ProgramComplianceSummary> lowestCompliancePrograms;

    @Data @Builder
    public static class ProgramComplianceSummary {
        private Long   programId;
        private String programCode;
        private String programTitle;
        private long   totalAssigned;
        private long   completed;
        private double complianceRate;
    }
}
