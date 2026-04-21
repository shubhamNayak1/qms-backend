package com.qms.module.dms.dto.request;

import com.qms.module.dms.enums.AccessLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Schema(description = "Update document metadata — null fields are ignored. File content cannot be changed; upload a new version instead.")
public class UpdateDocumentRequest {

    @Size(max = 300)
    private String title;

    private String description;
    private String department;
    private String tags;
    private AccessLevel accessLevel;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiryDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate reviewDate;

    private String changeSummary;
    private Boolean isControlled;
    private Long ownerId;
}
