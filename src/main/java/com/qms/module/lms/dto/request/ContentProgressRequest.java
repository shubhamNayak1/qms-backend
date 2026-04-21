package com.qms.module.lms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Report a user's progress on a content item")
public class ContentProgressRequest {

    @NotNull
    private Long contentId;

    @Min(0) @Max(100)
    @Schema(description = "View percentage (0–100). Set to 100 to mark complete.", example = "100")
    private Integer viewPercent;

    @Schema(description = "True when the user acknowledges reading the document", example = "true")
    private Boolean acknowledged;

    @Schema(description = "Seconds actively spent on this item in this session", example = "300")
    private Long sessionTimeSeconds;
}
