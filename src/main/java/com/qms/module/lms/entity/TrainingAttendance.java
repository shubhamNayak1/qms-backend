package com.qms.module.lms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Records whether a specific trainee attended a TrainingSession.
 *
 * One row per (session, enrollment) pair.
 * Attendance can only be marked within the ±2-day window of the session date.
 *
 * Relationships:
 *   Many TrainingAttendance → One TrainingSession
 *   One  TrainingAttendance → One Enrollment  (enrollment of the trainee)
 */
@Entity
@Table(
    name = "lms_training_attendance",
    uniqueConstraints = {
        @UniqueConstraint(
            name        = "uk_attendance_session_enrollment",
            columnNames = {"session_id", "enrollment_id"}
        )
    },
    indexes = {
        @Index(name = "idx_att_session",    columnList = "session_id"),
        @Index(name = "idx_att_enrollment", columnList = "enrollment_id"),
        @Index(name = "idx_att_user",       columnList = "user_id")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrainingAttendance {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private TrainingSession session;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private Long enrollmentId;

    // ── Trainee snapshot ──────────────────────────────────────

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", length = 150)
    private String userName;

    // ── Attendance record ─────────────────────────────────────

    @Column(name = "is_present", nullable = false)
    @Builder.Default
    private Boolean isPresent = false;

    /** Actual date the trainee was marked present (within ±2 day window). */
    @Column(name = "attendance_date")
    private LocalDate attendanceDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ── Marked by ─────────────────────────────────────────────

    @Column(name = "marked_by", length = 150)
    private String markedBy;

    @Column(name = "marked_at")
    private LocalDateTime markedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist private void onCreate() { createdAt = LocalDateTime.now(); }
}
