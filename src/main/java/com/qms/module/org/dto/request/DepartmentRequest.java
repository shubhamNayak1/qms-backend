package com.qms.module.org.dto.request;

import com.qms.module.org.enums.DepartmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Create / update a Department")
public class DepartmentRequest {

    @NotBlank(message = "Department name is required")
    @Size(max = 150)
    private String name;

    @NotBlank(message = "Department code is required")
    @Size(max = 30)
    @Schema(example = "QA",
            description = "Short stable code; must be unique. Used in URLs, exports, and audit trails.")
    private String code;

    @Size(max = 500)
    private String description;

    @NotNull(message = "Site id is required")
    private Long siteId;

    /** Null = top-level department under the site. */
    private Long parentId;

    /** users.id of the Head of Department — optional. */
    private Long hodUserId;

    @NotNull(message = "Department type is required")
    @Schema(example = "STANDARD")
    private DepartmentType deptType;
}
