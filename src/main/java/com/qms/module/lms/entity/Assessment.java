package com.qms.module.lms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Assessment (quiz/exam) associated with a TrainingProgram.
 *
 * One program has at most one Assessment definition.
 * Each enrollment attempt generates an AssessmentAttempt record.
 *
 * Relationships:
 *   One Assessment → One TrainingProgram (owner)
 *   One Assessment → Many AssessmentQuestion
 */
@Entity
@Table(name = "lms_assessments",
       indexes = @Index(name = "idx_assessment_program", columnList = "program_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Assessment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, updatable = false, unique = true)
    private TrainingProgram program;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    /** Time limit in minutes. Null = no time limit. */
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    /** Minimum score to pass (overrides program.passScore for this assessment). */
    @Column(name = "pass_score", nullable = false)
    @Builder.Default
    private Integer passScore = 80;

    /** Whether questions are randomised per attempt. */
    @Column(name = "randomise_questions", nullable = false)
    @Builder.Default
    private Boolean randomiseQuestions = false;

    /** Whether answer options are randomised per attempt. */
    @Column(name = "randomise_answers", nullable = false)
    @Builder.Default
    private Boolean randomiseAnswers = false;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<AssessmentQuestion> questions = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist  private void onCreate() { createdAt = LocalDateTime.now(); }
    @PreUpdate   private void onUpdate() { updatedAt = LocalDateTime.now(); }

    public int getTotalMarks() {
        return questions.stream().mapToInt(q -> q.getMarks() != null ? q.getMarks() : 1).sum();
    }
}
