package com.qms.module.qms.complaint.dto.request;

import com.qms.module.qms.common.dto.request.QmsBaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Request body for creating or updating a Market Complaint record")
public class MarketComplaintRequest extends QmsBaseRequest {

    private String customerName;
    private String customerContact;
    private String customerCountry;
    private String productName;
    private String batchNumber;
    private LocalDate expiryDate;

    @Schema(example = "Quality", description = "Quality / Safety / Packaging / Labeling / Delivery / Service")
    private String complaintCategory;

    @Schema(example = "Email", description = "Phone / Email / Portal / Field Visit / Letter")
    private String complaintSource;

    private LocalDate receivedDate;
    private Boolean   reportableToAuthority;
    private String    authorityReportReference;
    private LocalDate authorityReportDate;
    private String    resolutionDetails;
    private String    customerResponse;
    private LocalDate customerNotifiedDate;
    private Boolean   customerSatisfied;
    private String    capaReference;
    private Boolean   sampleReturned;
}
