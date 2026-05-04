package com.qms.module.qms.common.dto.response;

import com.qms.common.enums.QmsRecordType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class QmsLineItemResponse {
    private Long          id;
    private QmsRecordType recordType;
    private Long          recordId;
    private Integer       srNo;
    private String        existingSystem;
    private String        proposedSystem;
    private String        justification;
    private Long          proposedById;
    private String        proposedByName;
    private LocalDate     proposedDate;
    private String        status;
    private String        remark;
    private Long          checkedById;
    private String        checkedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
