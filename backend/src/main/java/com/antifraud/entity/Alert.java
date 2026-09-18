package com.antifraud.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "alerts")
public class Alert {
    @Id @Column(length = 64) private String alertId;
    @Column(length = 64) private String logId;
    private LocalDateTime timestamp = LocalDateTime.now();
    private String userId;
    private Integer alertLevel;  // 1=关注 2=高危 3=紧急
    @Column(length = 256) private String title;
    @Column(columnDefinition = "TEXT") private String detail;
    private Double riskScore;
    @Column(columnDefinition = "TEXT") private String hitRules;
    @Column(length = 16) private String status = "pending"; // pending/resolved

    public String getAlertId() { return alertId; } public void setAlertId(String alertId) { this.alertId = alertId; }
    public String getLogId() { return logId; } public void setLogId(String logId) { this.logId = logId; }
    public LocalDateTime getTimestamp() { return timestamp; } public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getUserId() { return userId; } public void setUserId(String userId) { this.userId = userId; }
    public Integer getAlertLevel() { return alertLevel; } public void setAlertLevel(Integer alertLevel) { this.alertLevel = alertLevel; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getDetail() { return detail; } public void setDetail(String detail) { this.detail = detail; }
    public Double getRiskScore() { return riskScore; } public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
    public String getHitRules() { return hitRules; } public void setHitRules(String hitRules) { this.hitRules = hitRules; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
}

