package com.qms.module.lms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.exception.AppException;
import com.qms.module.lms.dto.request.AssessmentQuestionRequest;
import com.qms.module.lms.dto.request.AssessmentSetupRequest;
import com.qms.module.lms.dto.response.AssessmentDetailResponse;
import com.qms.module.lms.dto.response.AssessmentQuestionResponse;
import com.qms.module.lms.entity.Assessment;
import com.qms.module.lms.entity.AssessmentQuestion;
import com.qms.module.lms.entity.TrainingProgram;
import com.qms.module.lms.repository.AssessmentQuestionRepository;
import com.qms.module.lms.repository.AssessmentRepository;
import com.qms.module.lms.repository.TrainingProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgramAssessmentService {

    private final TrainingProgramRepository  programRepository;
    private final AssessmentRepository       assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final ObjectMapper               objectMapper;

    // ── Get full assessment (managers only — includes correct answers) ──

    public AssessmentDetailResponse getAssessment(Long programId) {
        Assessment assessment = findOrCreateAssessment(programId);
        return toDetailResponse(assessment);
    }

    // ── Create / update assessment metadata ────────────────────

    @Transactional
    public AssessmentDetailResponse setupAssessment(Long programId, AssessmentSetupRequest req) {
        Assessment assessment = findOrCreateAssessment(programId);

        if (req.getTitle()              != null) assessment.setTitle(req.getTitle());
        if (req.getInstructions()       != null) assessment.setInstructions(req.getInstructions());
        if (req.getTimeLimitMinutes()   != null) assessment.setTimeLimitMinutes(req.getTimeLimitMinutes());
        if (req.getPassScore()          != null) assessment.setPassScore(req.getPassScore());
        if (req.getRandomiseQuestions() != null) assessment.setRandomiseQuestions(req.getRandomiseQuestions());
        if (req.getRandomiseAnswers()   != null) assessment.setRandomiseAnswers(req.getRandomiseAnswers());

        assessment = assessmentRepository.save(assessment);
        log.info("Assessment updated for programId={}", programId);
        return toDetailResponse(assessment);
    }

    // ── Add question ─────────────────────────────────────────

    @Transactional
    public AssessmentDetailResponse addQuestion(Long programId, AssessmentQuestionRequest req) {
        Assessment assessment = findOrCreateAssessment(programId);
        validateQuestionRequest(req);

        // Auto-assign display order if not provided
        int order = req.getDisplayOrder() != null ? req.getDisplayOrder()
                : (int) questionRepository.countByAssessment_Id(assessment.getId()) + 1;

        AssessmentQuestion question = AssessmentQuestion.builder()
                .assessment(assessment)
                .questionType(req.getQuestionType())
                .questionText(req.getQuestionText())
                .options(req.getOptions())
                .correctAnswer(req.getCorrectAnswer())
                .explanation(req.getExplanation())
                .marks(req.getMarks() != null ? req.getMarks() : 1)
                .displayOrder(order)
                .build();

        questionRepository.save(question);
        log.info("Question added to assessment for programId={}", programId);

        // Reload to reflect updated list
        return toDetailResponse(assessmentRepository.findByProgram_Id(programId)
                .orElseThrow(() -> AppException.notFound("Assessment for program", programId)));
    }

    // ── Update question ──────────────────────────────────────

    @Transactional
    public AssessmentDetailResponse updateQuestion(Long programId, Long questionId,
                                                    AssessmentQuestionRequest req) {
        Assessment assessment = findOrCreateAssessment(programId);
        AssessmentQuestion q  = questionRepository
                .findByIdAndAssessment_Id(questionId, assessment.getId())
                .orElseThrow(() -> AppException.notFound("Question", questionId));

        if (req.getQuestionType()  != null) q.setQuestionType(req.getQuestionType());
        if (req.getQuestionText()  != null) q.setQuestionText(req.getQuestionText());
        if (req.getOptions()       != null) q.setOptions(req.getOptions());
        if (req.getCorrectAnswer() != null) q.setCorrectAnswer(req.getCorrectAnswer());
        if (req.getExplanation()   != null) q.setExplanation(req.getExplanation());
        if (req.getMarks()         != null) q.setMarks(req.getMarks());
        if (req.getDisplayOrder()  != null) q.setDisplayOrder(req.getDisplayOrder());

        validateQuestionRequest(req.getQuestionType() != null ? req : buildFullRequest(q));

        questionRepository.save(q);
        log.info("Question {} updated for programId={}", questionId, programId);
        return toDetailResponse(assessmentRepository.findByProgram_Id(programId)
                .orElseThrow(() -> AppException.notFound("Assessment for program", programId)));
    }

    // ── Delete question ──────────────────────────────────────

    @Transactional
    public void deleteQuestion(Long programId, Long questionId) {
        Assessment assessment = findOrCreateAssessment(programId);
        AssessmentQuestion q  = questionRepository
                .findByIdAndAssessment_Id(questionId, assessment.getId())
                .orElseThrow(() -> AppException.notFound("Question", questionId));
        questionRepository.delete(q);
        log.info("Question {} deleted from programId={}", questionId, programId);
    }

    // ── Internal: find or auto-create assessment ──────────────

    /**
     * Returns the existing Assessment for the program, or creates a new empty one.
     * Auto-creation ensures examEnabled programs always have an Assessment record
     * without requiring a separate explicit setup call.
     */
    @Transactional
    public Assessment findOrCreateAssessment(Long programId) {
        return assessmentRepository.findByProgram_Id(programId).orElseGet(() -> {
            TrainingProgram program = programRepository.findByIdAndIsDeletedFalse(programId)
                    .orElseThrow(() -> AppException.notFound("Training Program", programId));

            Assessment assessment = Assessment.builder()
                    .program(program)
                    .title(program.getTitle() + " — Assessment")
                    .passScore(program.getPassScore() != null ? program.getPassScore() : 80)
                    .randomiseQuestions(false)
                    .randomiseAnswers(false)
                    .build();

            // Mark program as exam-enabled if it wasn't already
            if (!Boolean.TRUE.equals(program.getExamEnabled())) {
                program.setExamEnabled(true);
                program.setAssessmentRequired(true);
                programRepository.save(program);
            }

            Assessment saved = assessmentRepository.save(assessment);
            log.info("Auto-created Assessment for programId={}", programId);
            return saved;
        });
    }

    // ── Helper: questions for trainee (no correct answers) ────

    public List<AssessmentQuestionResponse> getQuestionsForTaker(Long assessmentId) {
        return questionRepository.findByAssessment_IdOrderByDisplayOrderAsc(assessmentId)
                .stream()
                .map(this::toQuestionResponse)
                .toList();
    }

    // ── Validation ────────────────────────────────────────────

    private void validateQuestionRequest(AssessmentQuestionRequest req) {
        if (req.getQuestionType() == null) return;
        switch (req.getQuestionType()) {
            case MULTIPLE_CHOICE, TRUE_FALSE, MULTI_SELECT -> {
                if (req.getOptions() == null || req.getOptions().isBlank()) {
                    throw AppException.badRequest(
                            req.getQuestionType() + " questions require options");
                }
                if (req.getCorrectAnswer() == null || req.getCorrectAnswer().isBlank()) {
                    throw AppException.badRequest(
                            req.getQuestionType() + " questions require a correctAnswer");
                }
            }
            case SHORT_ANSWER -> { /* no options or correctAnswer needed */ }
        }
    }

    private AssessmentQuestionRequest buildFullRequest(AssessmentQuestion q) {
        AssessmentQuestionRequest r = new AssessmentQuestionRequest();
        r.setQuestionType(q.getQuestionType());
        r.setOptions(q.getOptions());
        r.setCorrectAnswer(q.getCorrectAnswer());
        return r;
    }

    // ── Response mappers ──────────────────────────────────────

    private AssessmentDetailResponse toDetailResponse(Assessment a) {
        List<AssessmentDetailResponse.QuestionWithAnswerResponse> questions =
                a.getQuestions().stream()
                        .map(q -> AssessmentDetailResponse.QuestionWithAnswerResponse.builder()
                                .id(q.getId())
                                .questionType(q.getQuestionType() != null ? q.getQuestionType().name() : null)
                                .questionText(q.getQuestionText())
                                .options(parseOptions(q.getOptions()))
                                .correctAnswer(q.getCorrectAnswer())
                                .explanation(q.getExplanation())
                                .marks(q.getMarks())
                                .displayOrder(q.getDisplayOrder())
                                .build())
                        .toList();

        return AssessmentDetailResponse.builder()
                .id(a.getId())
                .programId(a.getProgram() != null ? a.getProgram().getId() : null)
                .programTitle(a.getProgram() != null ? a.getProgram().getTitle() : null)
                .title(a.getTitle())
                .instructions(a.getInstructions())
                .timeLimitMinutes(a.getTimeLimitMinutes())
                .passScore(a.getPassScore())
                .randomiseQuestions(a.getRandomiseQuestions())
                .randomiseAnswers(a.getRandomiseAnswers())
                .totalMarks(a.getTotalMarks())
                .questionCount(questions.size())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .questions(questions)
                .build();
    }

    AssessmentQuestionResponse toQuestionResponse(AssessmentQuestion q) {
        return AssessmentQuestionResponse.builder()
                .id(q.getId())
                .questionType(q.getQuestionType())
                .questionText(q.getQuestionText())
                .options(parseOptions(q.getOptions()))
                .explanation(null) // hidden until after submission
                .marks(q.getMarks())
                .displayOrder(q.getDisplayOrder())
                .build();
    }

    private List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return null;
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            // Options might be a plain comma-separated string (legacy)
            return List.of(optionsJson.split(","));
        }
    }
}
