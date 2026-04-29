package com.qms.module.reports.dto.response;

import com.qms.module.reports.enums.ReportModule;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class ModuleFieldsResponse {
    private ReportModule    module;
    private List<FieldInfo> dimensions;
    private List<FieldInfo> metrics;

    @Data @Builder
    public static class FieldInfo {
        private String key;
        private String label;
    }
}
