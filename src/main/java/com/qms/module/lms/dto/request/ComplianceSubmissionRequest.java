package com.qms.module.lms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Trainee's compliance evidence submission after training")
public class ComplianceSubmissionRequest {

    @Schema(description = "Storage key of the uploaded attachment (from file upload endpoint)",
            example = "uploads/lms/compliance/enroll-42-proof.pdf")
    private String attachmentStorageKey;

    @Schema(example = "attendance-proof.pdf")
    private String attachmentFileName;

    private Long attachmentFileSizeBytes;

    /**
     * JSON array of Q&A answers, e.g.:
     * [{"question":"Define GMP","answer":"Good Manufacturing Practice..."}]
     */
    @Schema(description = "JSON array of question-answer pairs for configured Q&A",
            example = "[{\"question\":\"What is GMP?\",\"answer\":\"Good Manufacturing Practice\"}]")
    private String qnaAnswers;
}
