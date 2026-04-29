package com.qms.module.lms.controller;

import com.qms.common.response.ApiResponse;
import com.qms.module.lms.dto.request.AssessmentQuestionRequest;
import com.qms.module.lms.dto.request.AssessmentSetupRequest;
import com.qms.module.lms.dto.response.AssessmentDetailResponse;
import com.qms.module.lms.service.ProgramAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lms/programs/{programId}/assessment")
@RequiredArgsConstructor
@Tag(name = "LMS — Assessment Setup", description = "Manage MCQ questions for a program's online exam")
@SecurityRequirement(name = "bearerAuth")
public class ProgramAssessmentController {

    private final ProgramAssessmentService assessmentService;

    // ── GET assessment (manager view with correct answers) ────

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','TRAINER','COORDINATOR')")
    @Operation(summary = "Get full assessment definition including questions and correct answers",
               description = """
                   Returns the assessment attached to this program, including all questions
                   with their correct answers. **Manager/trainer role only — do not expose to trainees.**
                   If no assessment exists yet and the program has examEnabled=true, one is auto-created.
                   """)
    public ResponseEntity<ApiResponse<AssessmentDetailResponse>> getAssessment(
            @PathVariable Long programId) {
        return ApiResponse.ok(assessmentService.getAssessment(programId));
    }

    // ── Update assessment metadata (title, passScore, timeLimit, etc.) ────

    @PutMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER')")
    @Operation(summary = "Update assessment settings (title, pass score, time limit, randomisation)",
               description = "All fields are optional — only provided fields are updated.")
    public ResponseEntity<ApiResponse<AssessmentDetailResponse>> setup(
            @PathVariable Long programId,
            @Valid @RequestBody AssessmentSetupRequest req) {
        return ApiResponse.ok("Assessment updated", assessmentService.setupAssessment(programId, req));
    }

    // ── Add a question ────────────────────────────────────────

    @PostMapping("/questions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','TRAINER')")
    @Operation(summary = "Add an MCQ / TRUE_FALSE / MULTI_SELECT / SHORT_ANSWER question",
               description = """
                   Adds a new question to the program's assessment.

                   **Question types:**
                   - `MULTIPLE_CHOICE` — single correct answer from a list
                   - `MULTI_SELECT`    — one or more correct answers (comma-separated in correctAnswer)
                   - `TRUE_FALSE`      — options: ["True","False"]
                   - `SHORT_ANSWER`    — free-text, manually graded (correctAnswer not required)

                   **options** must be a JSON array string, e.g. `["Option A","Option B","Option C"]`

                   **correctAnswer** for MULTI_SELECT: `"Option A,Option C"` (comma-separated)
                   """)
    public ResponseEntity<ApiResponse<AssessmentDetailResponse>> addQuestion(
            @PathVariable Long programId,
            @Valid @RequestBody AssessmentQuestionRequest req) {
        return ApiResponse.created("Question added", assessmentService.addQuestion(programId, req));
    }

    // ── Update a question ────────────────────────────────────

    @PutMapping("/questions/{questionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','TRAINER')")
    @Operation(summary = "Update an existing question",
               description = "All fields are optional — only provided fields are updated.")
    public ResponseEntity<ApiResponse<AssessmentDetailResponse>> updateQuestion(
            @PathVariable Long programId,
            @PathVariable Long questionId,
            @Valid @RequestBody AssessmentQuestionRequest req) {
        return ApiResponse.ok("Question updated",
                assessmentService.updateQuestion(programId, questionId, req));
    }

    // ── Delete a question ────────────────────────────────────

    @DeleteMapping("/questions/{questionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TRAINING_MANAGER','QA_MANAGER','TRAINER')")
    @Operation(summary = "Delete a question from the assessment")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable Long programId,
            @PathVariable Long questionId) {
        assessmentService.deleteQuestion(programId, questionId);
        return ApiResponse.ok("Question deleted", null);
    }
}
