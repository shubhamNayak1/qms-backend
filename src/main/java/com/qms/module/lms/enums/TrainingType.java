package com.qms.module.lms.enums;

/**
 * Top-level classification of a training program.
 *
 *  SCHEDULED  — Instructor-led session with a fixed date, venue/link, and trainer.
 *               Trainee must attend and submit compliance proof.
 *
 *  SELF       — Self-paced. Trainee reads/watches material independently.
 *               Coordinator reviews completion before certificate is issued.
 *
 *  INDUCTION  — New-employee onboarding. Multi-department, requires
 *               HR review then QA Head approval. Generates a TNI on completion.
 */
public enum TrainingType {
    SCHEDULED,
    SELF,
    INDUCTION
}
