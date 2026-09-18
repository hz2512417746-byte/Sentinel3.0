package com.antifraud.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "log_events", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_src_ip", columnList = "srcIp"),
    @Index(name = "idx_event_type", columnList = "eventType"),
    @Index(name = "idx_risk_level", columnList = "riskLevel"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class LogEvent {
    @Id
    @Column(length = 64)
    private String logId;

    private LocalDateTime timestamp;
    private String sessionId;
    private String userId;
    private String srcIp;
    private Integer srcPort;
    private String geoCity;
    private String eventType;
    private String eventStatus;
    private String deviceId;
    private String deviceType;
    private String connection;

    // DPI 通联字段
    private String domain;
    @Column(columnDefinition = "TEXT")
    private String url;
    private String appName;
    private String contactPhone;

    // 运营商信令字段（对接 HTTP/HTTPS 信令 + 湖州涉诈分类）
    private String msisdn;        // 用户手机号（MSISDN）
    private Integer tac;          // 位置区（TAC）
    private Long cellId;          // 小区（Cell ID）
    private String lacCi;         // 基站 ID（lac_ci）
    private Integer appType;      // 应用大类（App Type）
    private Long appSubType;      // 应用小类（App Sub-type）
    private Integer appContent;   // 内容细分（App Content）
    private Integer appStatus;    // 会话状态 0-7（App Status）
    private Long dlData;          // 下行流量字节（DL Data）
    private String serverIp;      // 服务器 IP（App Server IP）
    private Integer serverPort;   // 服务器端口（App Server Port）
    private String sni;           // HTTPS 服务器名称（SNI）
    private String referUri;      // 参考 URI（refer_URI）
    private String fraudUrlCategory; // 涉诈网址分类（湖州 15 种之一）

    private Integer fraudLabel = 0;

    // 分析结果
    private Double riskScore;
    private Integer riskLevel;
    private Double mlScore;
    private Double ruleScore;
    private Double seqScore;
    private Double immuneScore;
    @Column(columnDefinition = "TEXT")
    private String hitRules;
    @Column(length = 16)
    private String decision = "allow";
    private Boolean isBlocked = false;
    private String blockReason;
    private LocalDateTime analyzedAt;

    // 图谱分析结果
    private Double graphScore;
    @Column(length = 64)
    private String graphMatchType;
    @Column(length = 1024)
    private String graphExplanation;

    // 文本识别结果
    @Column(columnDefinition = "TEXT")
    private String textContent;
    @Column(length = 16)
    private String inputType;
    @Column(length = 64)
    private String fraudType;
    private Double fraudConfidence;

    @Column(columnDefinition = "TEXT")
    private String rawJson;

    // Getters and Setters
    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSrcIp() { return srcIp; }
    public void setSrcIp(String srcIp) { this.srcIp = srcIp; }
    public Integer getSrcPort() { return srcPort; }
    public void setSrcPort(Integer srcPort) { this.srcPort = srcPort; }
    public String getGeoCity() { return geoCity; }
    public void setGeoCity(String geoCity) { this.geoCity = geoCity; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEventStatus() { return eventStatus; }
    public void setEventStatus(String eventStatus) { this.eventStatus = eventStatus; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getConnection() { return connection; }
    public void setConnection(String connection) { this.connection = connection; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public Integer getTac() { return tac; }
    public void setTac(Integer tac) { this.tac = tac; }
    public Long getCellId() { return cellId; }
    public void setCellId(Long cellId) { this.cellId = cellId; }
    public String getLacCi() { return lacCi; }
    public void setLacCi(String lacCi) { this.lacCi = lacCi; }
    public Integer getAppType() { return appType; }
    public void setAppType(Integer appType) { this.appType = appType; }
    public Long getAppSubType() { return appSubType; }
    public void setAppSubType(Long appSubType) { this.appSubType = appSubType; }
    public Integer getAppContent() { return appContent; }
    public void setAppContent(Integer appContent) { this.appContent = appContent; }
    public Integer getAppStatus() { return appStatus; }
    public void setAppStatus(Integer appStatus) { this.appStatus = appStatus; }
    public Long getDlData() { return dlData; }
    public void setDlData(Long dlData) { this.dlData = dlData; }
    public String getServerIp() { return serverIp; }
    public void setServerIp(String serverIp) { this.serverIp = serverIp; }
    public Integer getServerPort() { return serverPort; }
    public void setServerPort(Integer serverPort) { this.serverPort = serverPort; }
    public String getSni() { return sni; }
    public void setSni(String sni) { this.sni = sni; }
    public String getReferUri() { return referUri; }
    public void setReferUri(String referUri) { this.referUri = referUri; }
    public String getFraudUrlCategory() { return fraudUrlCategory; }
    public void setFraudUrlCategory(String fraudUrlCategory) { this.fraudUrlCategory = fraudUrlCategory; }
    public Integer getFraudLabel() { return fraudLabel; }
    public void setFraudLabel(Integer fraudLabel) { this.fraudLabel = fraudLabel; }
    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
    public Integer getRiskLevel() { return riskLevel; }
    public void setRiskLevel(Integer riskLevel) { this.riskLevel = riskLevel; }
    public Double getMlScore() { return mlScore; }
    public void setMlScore(Double mlScore) { this.mlScore = mlScore; }
    public Double getRuleScore() { return ruleScore; }
    public void setRuleScore(Double ruleScore) { this.ruleScore = ruleScore; }
    public Double getSeqScore() { return seqScore; }
    public void setSeqScore(Double seqScore) { this.seqScore = seqScore; }
    public Double getImmuneScore() { return immuneScore; }
    public void setImmuneScore(Double immuneScore) { this.immuneScore = immuneScore; }
    public String getHitRules() { return hitRules; }
    public void setHitRules(String hitRules) { this.hitRules = hitRules; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public Boolean getIsBlocked() { return isBlocked; }
    public void setIsBlocked(Boolean isBlocked) { this.isBlocked = isBlocked; }
    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
    public Double getGraphScore() { return graphScore; }
    public void setGraphScore(Double v) { this.graphScore = v; }
    public String getGraphMatchType() { return graphMatchType; }
    public void setGraphMatchType(String v) { this.graphMatchType = v; }
    public String getGraphExplanation() { return graphExplanation; }
    public void setGraphExplanation(String v) { this.graphExplanation = v; }
    public String getTextContent() { return textContent; }
    public void setTextContent(String v) { this.textContent = v; }
    public String getInputType() { return inputType; }
    public void setInputType(String v) { this.inputType = v; }
    public String getFraudType() { return fraudType; }
    public void setFraudType(String v) { this.fraudType = v; }
    public Double getFraudConfidence() { return fraudConfidence; }
    public void setFraudConfidence(Double v) { this.fraudConfidence = v; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
}
