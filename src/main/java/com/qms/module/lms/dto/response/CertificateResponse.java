package com.qms.module.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qms.module.lms.enums.CertificateStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CertificateResponse {

    private Long              id;
    private String            certificateNumber;
    private Long              userId;
    private String            userName;
    private Long              programId;
    private String            programCode;
    private String            programTitle;
    private String            issuer;
    private LocalDate         issuedDate;
    private LocalDate         expiryDate;
    private CertificateStatus status;
    private Integer           scoreAchieved;
    private String            revokedReason;
    private LocalDateTime     createdAt;
    private boolean           expired;
}
