package com.qms.module.reports.enums;

public enum ReportStatus {
    PENDING,    // created but never run
    RUNNING,    // currently being generated
    COMPLETED,  // file ready for download
    FAILED,     // last run failed
    DISABLED    // manually disabled
}
