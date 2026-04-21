package com.qms.module.qms.complaint.dto.response;

import com.qms.module.qms.common.dto.response.QmsBaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MarketComplaintResponse extends QmsBaseResponse {
    private String    customerName;
    private String    customerContact;
    private String    customerCountry;
    private String    productName;
    private String    batchNumber;
    private LocalDate expiryDate;
    private String    complaintCategory;
    private String    complaintSource;
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
