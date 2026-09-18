package com.antifraud.entity;

/** 告警处理状态 */
public enum AlertStatus {
    PENDING("pending"),
    RESOLVED("resolved");

    private final String dbValue;
    AlertStatus(String dbValue) { this.dbValue = dbValue; }
    public String getDbValue() { return dbValue; }
    public static AlertStatus fromDbValue(String value) {
        for (AlertStatus s : values()) {
            if (s.dbValue.equals(value)) return s;
        }
        return PENDING;
    }
}
