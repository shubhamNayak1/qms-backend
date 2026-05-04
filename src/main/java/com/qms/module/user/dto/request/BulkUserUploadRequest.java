package com.qms.module.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Bulk import payload. The admin uploads a list of {@link CreateUserRequest}
 * payloads and the service processes them in one transaction (success or
 * fail per row, summarised in the response).
 *
 * Newly created users are NOT auto-licensed — admins still have to assign a
 * license individually so they can stagger seat costs as testers come online.
 */
@Data
@Schema(description = "Bulk user upload — list of CreateUserRequest")
public class BulkUserUploadRequest {

    @NotEmpty(message = "At least one user is required")
    @Size(max = 500, message = "Bulk upload is capped at 500 users per call")
    @Valid
    private List<CreateUserRequest> users;

    /**
     * If true, generate a default password (User initials + joining year +
     * "@123") for any row whose `password` field is blank. Useful when HR
     * exports an employee list and you don't want to assign a password
     * up-front. The user will still be forced to change on first login.
     */
    private Boolean autoGeneratePasswords;
}
