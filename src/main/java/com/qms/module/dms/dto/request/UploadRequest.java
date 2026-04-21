package com.qms.module.dms.dto.request;

import com.qms.module.dms.enums.AccessLevel;
import com.qms.module.dms.enums.DocumentCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Metadata sent alongside the multipart file upload.
 * Submitted as a JSON part named "metadata" in a multipart/form-data request.
 *
 * Example curl:
 *   curl -X POST /api/v1/dms/documents \
 *     -H "Authorization: Bearer $TOKEN" \
 *     -F "file=@/path/to/sop.pdf" \
 *     -F 'metadata={"title":"Cleaning SOP","category":"SOP","department":"Manufacturing"};type=application/json'
 */
@Data
@Schema(description = "Metadata for a new document upload (sent as the 'metadata' JSON part)")
public class UploadRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 300, message = "Title must not exceed 300 characters")
    @Schema(example = "Cleaning and Sanitisation SOP — Fill Suite 3")
    private String title;

    @Schema(example = "Standard operating procedure for cleaning the fill suite after each batch.")
    private String description;

    @NotNull(message = "Category is required")
    @Schema(example = "SOP")
    private DocumentCategory category;

    @Schema(example = "Manufacturing")
    private String department;

    @Schema(description = "Comma-separated keywords for search", example = "cleaning,GMP,sanitisation,SOP")
    private String tags;

    @Schema(description = "Access restriction level", example = "PUBLIC")
    private AccessLevel accessLevel = AccessLevel.PUBLIC;

    @FutureOrPresent(message = "Effective date must be today or in the future")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(example = "2024-05-01")
    private LocalDate effectiveDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(example = "2026-05-01", description = "Leave blank for documents with no expiry")
    private LocalDate expiryDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(example = "2025-05-01", description = "Scheduled review date")
    private LocalDate reviewDate;

    @Schema(description = "Summary of changes from the previous version (required for v2.0+)")
    private String changeSummary;

    @Schema(description = "Whether this is a controlled document requiring distribution acknowledgement",
            example = "true")
    private Boolean isControlled = true;

    @Schema(description = "User ID of the document owner (defaults to the uploading user)")
    private Long ownerId;
}
