package com.qms.module.org.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SiteResponse {
    private Long          id;
    private String        name;
    private String        code;
    private String        address;
    private Long          headUserId;
    private String        headUserName;   // resolved on read
    private Boolean       isActive;
    private LocalDateTime createdAt;
}
