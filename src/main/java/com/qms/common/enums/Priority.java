package com.qms.common.enums;

public enum Priority {
    LOW, MEDIUM, HIGH, CRITICAL;

    public int slaDays() {
        return switch (this) {
            case CRITICAL -> 1;
            case HIGH     -> 3;
            case MEDIUM   -> 7;
            case LOW      -> 14;
        };
    }
}
