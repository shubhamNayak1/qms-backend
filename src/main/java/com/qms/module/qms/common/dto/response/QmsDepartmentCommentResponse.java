package com.qms.module.qms.common.dto.response;

import com.qms.common.enums.QmsRecordType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QmsDepartmentCommentResponse {
    private Long          id;
    private QmsRecordType recordType;
    private Long          recordId;
    private Long          departmentId;
    private String        departmentName;
    private String        status;        // PENDING / COMPLETED
    private String        comment;
    private Long          doneById;
    private String        doneByName;
    private LocalDateTime doneAt;
    private LocalDateTime createdAt;
}
