package com.qms.module.lms.entity;

import com.qms.module.lms.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A concrete scheduled run of a TrainingProgram.
 * A program can have multiple sessions (e.g., multiple batches or repeat runs).
 *
 * Relationships:
 *   Many TrainingSession → One TrainingProgram
 *   One  TrainingSession → Many TrainingAttendance
 */
@Entity
@Table(
    name = "lms_training_sessions",
    indexes = {
        @Index(name = "idx_session_program",  columnList = "program_id"),
        @Index(name = "idx_session_date",     columnList = "session_date"),
        @Index(name = "idx_session_status",   columnList = "status")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrainingSession {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, updatable = false)
    private TrainingProgram program;

    // ── Schedule ─────────────────────────────────────────────

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    /** For multi-day sessions. Null = single-day. */
    @Column(name = "session_end_date")
    private LocalDate sessionEndDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    // ── Location ─────────────────────────────────────────────

    @Column(name = "venue", length = 300)
    private String venue;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    // ── Trainer & coordinator for this session ────────────────

    @Column(name = "trainer_id")
    private Long trainerId;

    @Column(name = "trainer_name", length = 150)
    private String trainerName;

    @Column(name = "coordinator_id")
    private Long coordinatorId;

    @Column(name = "coordinator_name", length = 150)
    private String coordinatorName;

    // ── Capacity ─────────────────────────────────────────────

    @Column(name = "max_participants")
    private Integer maxParticipants;

    // ── Status ───────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.SCHEDULED;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ── Relationships ─────────────────────────────────────────

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TrainingAttendance> attendances = new ArrayList<>();

    // ── Audit ─────────────────────────────────────────────────

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @PrePersist  private void onCreate() { createdAt = LocalDateTime.now(); }
    @PreUpdate   private void onUpdate() { updatedAt = LocalDateTime.now(); }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Returns true if today is within ±2 days of the session date.
     * Trainees may mark attendance within this window.
     */
    public boolean isWithinAttendanceWindow() {
        if (sessionDate == null) return false;
        LocalDate today = LocalDate.now();
        return !today.isBefore(sessionDate.minusDays(2))
            && !today.isAfter((sessionEndDate != null ? sessionEndDate : sessionDate).plusDays(2));
    }
}
