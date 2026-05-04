package com.qms.module.qms.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request a comment from a specific department, or fill one in.")
public class QmsDepartmentCommentRequest {

    /** Departments.id — required when creating a new comment row (QA routes a record). */
    @NotNull
    private Long departmentId;

    /** Optional — populated when the dept HOD actually fills the comment. */
    @Size(max = 5000)
    private String comment;
}
