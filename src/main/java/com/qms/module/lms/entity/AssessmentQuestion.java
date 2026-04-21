package com.qms.module.lms.entity;

import com.qms.module.lms.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;

/**
 * A single question within an Assessment.
 *
 * Options and the correct answer are stored as JSON strings for flexibility.
 * For MULTIPLE_CHOICE / MULTI_SELECT:
 *   options       = ["Option A","Option B","Option C","Option D"]
 *   correctAnswer = "Option A"  (or comma-separated for MULTI_SELECT)
 *
 * For TRUE_FALSE:
 *   options       = ["True","False"]
 *   correctAnswer = "True"
 *
 * For SHORT_ANSWER:
 *   options       = null
 *   correctAnswer = null (manually graded by trainer)
 *
 * Relationships:
 *   Many AssessmentQuestion → One Assessment
 */
@Entity
@Table(name = "lms_assessment_questions",
       indexes = @Index(name = "idx_aq_assessment", columnList = "assessment_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AssessmentQuestion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false, updatable = false)
    private Assessment assessment;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType questionType;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /** JSON array of answer options (null for SHORT_ANSWER). */
    @Column(name = "options", columnDefinition = "TEXT")
    private String options;

    /** Correct answer(s) — comma-separated for MULTI_SELECT. Null for SHORT_ANSWER. */
    @Column(name = "correct_answer", columnDefinition = "TEXT")
    private String correctAnswer;

    /** Explanation shown after the attempt. */
    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    /** Marks awarded for a correct answer (default 1). */
    @Column(name = "marks")
    @Builder.Default
    private Integer marks = 1;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 1;
}
