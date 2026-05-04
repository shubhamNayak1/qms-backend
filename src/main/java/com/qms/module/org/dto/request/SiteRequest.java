package com.qms.module.org.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Create / update a Site")
public class SiteRequest {

    @NotBlank(message = "Site name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 30)
    private String code;

    @Size(max = 500)
    private String address;

    /** users.id of the Site Head — optional (can be set later). */
    private Long headUserId;
}
