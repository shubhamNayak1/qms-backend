package com.qms.common.enums;

/**
 * Identifies the QMS sub-module type.
 * Used for record number prefixes, dashboard grouping, and audit module tagging.
 */
public enum QmsRecordType {
    CAPA             ("CAPA"),
    DEVIATION        ("DEV"),
    INCIDENT         ("INC"),
    CHANGE_CONTROL   ("CC"),
    MARKET_COMPLAINT ("MC");

    private final String prefix;

    QmsRecordType(String prefix) { this.prefix = prefix; }

    public String getPrefix() { return prefix; }
}
