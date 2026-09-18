package com.antifraud.graph;

/** 用户行为时间线事件 */
public class TimedEvent {
    public final long timestamp;
    public final String eventType;
    public final String accountId;
    public final double amount;
    public final String userId;

    public TimedEvent(long timestamp, String eventType, String accountId, double amount, String userId) {
        this.timestamp = timestamp; this.eventType = eventType; this.accountId = accountId;
        this.amount = amount; this.userId = userId;
    }

    /** 归一化为剧本匹配类型 */
    public String normalizedType() {
        if (eventType == null) return "UNKNOWN";
        return switch (eventType) {
            case "transfer" -> amount >= 50000 ? "TRANSFER_LARGE" : amount >= 5000 ? "TRANSFER_MEDIUM" : "TRANSFER_SMALL";
            case "login" -> "LOGIN";
            case "modify_password" -> "MODIFY_PASSWORD";
            case "add_payee" -> "ADD_PAYEE";
            case "verify_code" -> "VERIFY_CODE";
            case "contact" -> "CONTACT";
            default -> "UNKNOWN";
        };
    }
}
