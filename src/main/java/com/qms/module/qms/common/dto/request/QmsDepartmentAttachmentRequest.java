package com.qms.module.qms.common.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request body for inviting a department to upload OR for the department to fill its attachment row")
public class QmsDepartmentAttachmentRequest {

    @Schema(description = "Department id — required when inviting (POST), ignored on PUT (the row already knows its department)")
    private Long departmentId;

    @Schema(description = "Reference to the supporting DMS document. Use the DMS document id (preferred — title resolves automatically) or any free-text reference.")
    private String attachmentRef;

    @Schema(description = "Optional note from the uploading department alongside the attachment.")
    private String attachmentNote;
}
