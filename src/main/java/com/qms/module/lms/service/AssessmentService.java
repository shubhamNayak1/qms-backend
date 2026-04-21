package com.qms.module.lms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.exception.AppException;
import com.qms.module.lms.dto.request.AssessmentAnswerRequest;
import com.qms.module.lms.dto.response.AssessmentAttemptResponse;
import com.qms.module.lms.entity.*;
import com.qms.module.lms.enums.AssessmentStatus;
import com.qms.module.lms.enums.EnrollmentStatus;
import com.qms.module.lms.enums.QuestionType;
import com.qms.module.lms.repository.AssessmentAttemptRepository;
import com.qms.module.lms.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssessmentService {

    private final AssessmentAttemptRepository attemptRepository;
    private final EnrollmentRepository        enrollmentRepository;
    private final EnrollmentService           enrollmentService;
    private final ObjectMapper                objectMapper;

    // ── Start an attempt ─────────────────────────────────────

    @Transactional
    public AssessmentAttemptResponse startAttempt(Long enrollmentId) {
        Enrollment enrollment = enrollmentService.findById(enrollmentId);
        Assessment assessment = enrollment.getProgram().getAssessment();

        if (assessment == null) {
            throw AppException.badRequest("This training program has no assessment");
        }
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED
                || enrollment.getStatus() == EnrollmentStatus.CANCELLED
                || enrollment.getStatus() == EnrollmentStatus.WAIVED) {
            throw AppException.badRequest("Cannot start assessment for a " + enrollment.getStatus() + " enrollment");
        }

        // Block if already an active attempt
        attemptRepository.findActiveAttempt(enrollmentId).ifPresent(a -> {
            throw AppException.conflict("An assessment attempt is already in progress. Submit it first.");
        });

        // Check attempt limit
        int maxAttempts = enrollment.getProgram().getMaxAttempts();
        long used = attemptRepository.countByEnrollment_Id(enrollmentId);
        if (used >= maxAttempts) {
            throw AppException.badRequest(
                    "Maximum attempts reached (" + maxAttempts + "). Contact your manager to reset.");
        }

        int nextAttempt = (int) used + 1;
        AssessmentAttempt attempt = AssessmentAttempt.builder()
                .enrollment(enrollment)
                .assessment(assessment)
                .attemptNumber(nextAttempt)
                .status(AssessmentStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .totalMarks(assessment.getTotalMarks())
                .build();

        enrollment.setAttemptsUsed(nextAttempt);
        if (enrollment.getStatus() == EnrollmentStatus.ENROLLED) {
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollment.setStartedAt(LocalDateTime.now());
        }
        enrollmentRepository.save(enrollment);

        log.info("Assessment attempt {} started for enrollmentId={}", nextAttempt, enrollmentId);
        return toResponse(attemptRepository.save(attempt));
    }

    // ── Submit answers ────────────────────────────────────────

    @Transactional
    public AssessmentAttemptResponse submitAttempt(Long enrollmentId, AssessmentAnswerRequest req) {
        Enrollment enrollment = enrollmentService.findById(enrollmentId);
        AssessmentAttempt attempt = attemptRepository.findActiveAttempt(enrollmentId)
                .orElseThrow(() -> AppException.notFound(
                        "No active assessment attempt found for enrollment " + enrollmentId));

        Assessment assessment = attempt.getAssessment();
        boolean hasShortAnswer = assessment.getQuestions().stream()
                .anyMatch(q -> q.getQuestionType() == QuestionType.SHORT_ANSWER);

        // Persist answers JSON
        try {
            attempt.setAnswersJson(objectMapper.writeValueAsString(req.getAnswers()));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize answers: {}", e.getMessage());
        }

        if (hasShortAnswer) {
            // Requires manual review
            attempt.setStatus(AssessmentStatus.PENDING_REVIEW);
            attempt.setSubmittedAt(LocalDateTime.now());
        } else {
            // Auto-grade
            int rawScore = autoGrade(assessment.getQuestions(), req.getAnswers());
            int total    = assessment.getTotalMarks();
            int percent  = total == 0 ? 0 : (int) Math.round(rawScore * 100.0 / total);
            boolean passed = percent >= assessment.getPassScore();

            attempt.setRawScore(rawScore);
            attempt.setTotalMarks(total);
            attempt.setScorePercent(percent);
            attempt.setPassed(passed);
            attempt.setSubmittedAt(LocalDateTime.now());
            attempt.setStatus(passed ? AssessmentStatus.PASSED : AssessmentStatus.FAILED);

            enrollment.setLastScore(percent);
            enrollmentRepository.save(enrollment);

            if (passed) {
                enrollmentService.completeEnrollment(enrollment, percent);
                log.info("Assessment PASSED: enrollmentId={} score={}%", enrollmentId, percent);
            } else {
                enrollment.setStatus(EnrollmentStatus.FAILED);
                enrollmentRepository.save(enrollment);
                log.info("Assessment FAILED: enrollmentId={} score={}% (pass={}%)",
                        enrollmentId, percent, assessment.getPassScore());
            }
        }

        return toResponse(attemptRepository.save(attempt));
    }

    // ── Manual review (SHORT_ANSWER) ─────────────────────────

    @Transactional
    public AssessmentAttemptResponse reviewAttempt(Long attemptId, int scorePercent, String comments) {
        AssessmentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> AppException.notFound("Assessment attempt", attemptId));
        if (attempt.getStatus() != AssessmentStatus.PENDING_REVIEW) {
            throw AppException.badRequest("Attempt is not awaiting review. Status: " + attempt.getStatus());
        }
        if (scorePercent < 0 || scorePercent > 100) {
            throw AppException.badRequest("Score must be between 0 and 100");
        }

        Enrollment enrollment = attempt.getEnrollment();
        int passScore = attempt.getAssessment().getPassScore();
        boolean passed = scorePercent >= passScore;

        attempt.setScorePercent(scorePercent);
        attempt.setPassed(passed);
        attempt.setReviewerComments(comments);
        attempt.setReviewedAt(LocalDateTime.now());
        attempt.setReviewedBy(currentUsername());
        attempt.setStatus(passed ? AssessmentStatus.PASSED : AssessmentStatus.FAILED);

        enrollment.setLastScore(scorePercent);
        if (passed) {
            enrollmentService.completeEnrollment(enrollment, scorePercent);
        } else {
            enrollment.setStatus(EnrollmentStatus.FAILED);
            enrollmentRepository.save(enrollment);
        }
        return toResponse(attemptRepository.save(attempt));
    }

    public List<AssessmentAttemptResponse> getPendingReviews() {
        return attemptRepository.findPendingManualReview()
                .stream().map(this::toResponse).toList();
    }

    public List<AssessmentAttemptResponse> getAttemptsByEnrollment(Long enrollmentId) {
        return attemptRepository.findByEnrollment_IdOrderByAttemptNumberDesc(enrollmentId)
                .stream().map(this::toResponse).toList();
    }

    // ── Auto-grading ──────────────────────────────────────────

    private int autoGrade(List<AssessmentQuestion> questions, Map<Long, String> answers) {
        int score = 0;
        for (AssessmentQuestion q : questions) {
            if (q.getQuestionType() == QuestionType.SHORT_ANSWER) continue;
            String userAnswer   = answers.getOrDefault(q.getId(), "").trim();
            String correctAnswer = q.getCorrectAnswer() != null ? q.getCorrectAnswer().trim() : "";

            boolean correct = switch (q.getQuestionType()) {
                case MULTIPLE_CHOICE, TRUE_FALSE ->
                    userAnswer.equalsIgnoreCase(correctAnswer);
                case MULTI_SELECT -> {
                    Set<String> userSet    = normalizeSet(userAnswer);
                    Set<String> correctSet = normalizeSet(correctAnswer);
                    yield userSet.equals(correctSet);
                }
                default -> false;
            };
            if (correct) score += (q.getMarks() != null ? q.getMarks() : 1);
        }
        return score;
    }

    private Set<String> normalizeSet(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptySet();
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String s : csv.split(",")) result.add(s.trim());
        return result;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    AssessmentAttemptResponse toResponse(AssessmentAttempt a) {
        return AssessmentAttemptResponse.builder()
                .id(a.getId())
                .enrollmentId(a.getEnrollment() != null ? a.getEnrollment().getId() : null)
                .attemptNumber(a.getAttemptNumber())
                .status(a.getStatus())
                .rawScore(a.getRawScore())
                .totalMarks(a.getTotalMarks())
                .scorePercent(a.getScorePercent())
                .passed(a.getPassed())
                .passScore(a.getAssessment() != null ? a.getAssessment().getPassScore() : null)
                .reviewerComments(a.getReviewerComments())
                .startedAt(a.getStartedAt()).submittedAt(a.getSubmittedAt())
                .reviewedAt(a.getReviewedAt()).reviewedBy(a.getReviewedBy())
                .build();
    }
}
