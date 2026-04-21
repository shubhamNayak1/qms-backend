package com.qms.module.dms.enums;

/** Which storage backend is active. Injected via application.yml. */
public enum StorageStrategy {
    LOCAL,   // local filesystem — development / on-premise
    S3,      // AWS S3 — production cloud
    AZURE,   // Azure Blob Storage
    GCP      // Google Cloud Storage
}
