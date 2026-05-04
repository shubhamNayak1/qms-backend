package com.qms.module.org.dto.response;

import com.qms.module.org.enums.DepartmentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DepartmentResponse {
    private Long           id;
    private String         name;
    private String         code;
    private String         description;
    private Long           siteId;
    private Long           parentId;
    private String         parentName;     // resolved on read
    private Long           hodUserId;
    private String         hodUserName;    // resolved on read
    private DepartmentType deptType;
    private Boolean        isActive;
    private Integer        memberCount;    // populated by service
    private LocalDateTime  createdAt;
}
