package com.qms.module.lms.enums;

public enum CertificateStatus {
    ACTIVE,     // within validity period
    EXPIRED,    // validity period has lapsed — retraining required
    REVOKED     // withdrawn by an admin (e.g. compliance breach)
}
