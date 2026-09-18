package com.antifraud.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rule_configs")
public class RuleConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 64)
    private String feature;       // 简单规则：特征字段名（复杂规则用 conditionJson）

    @Column(nullable = false)
    private Double threshold;

    private Double weight = 1.0;

    @Column(length = 256)
    private String description;

    private Boolean enabled = true;
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── 复杂规则 + 三级预警扩展 ──
    @Column(length = 64)
    private String fraudType;      // 涉诈类型（对齐 FraudType 枚举）

    private Integer timeWindow;    // 时间窗口（分钟，35/120）

    private Integer riskLevel;     // 风险等级：1低/2中/3高

    @Column(columnDefinition = "TEXT")
    private String conditionJson;  // 复合条件 JSON：{"conditions":[{"feature":"..","threshold":..}]}

    // Getters & Setters
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getFeature() { return feature; } public void setFeature(String feature) { this.feature = feature; }
    public Double getThreshold() { return threshold; } public void setThreshold(Double threshold) { this.threshold = threshold; }
    public Double getWeight() { return weight; } public void setWeight(Double weight) { this.weight = weight; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public Boolean getEnabled() { return enabled; } public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getFraudType() { return fraudType; } public void setFraudType(String fraudType) { this.fraudType = fraudType; }
    public Integer getTimeWindow() { return timeWindow; } public void setTimeWindow(Integer timeWindow) { this.timeWindow = timeWindow; }
    public Integer getRiskLevel() { return riskLevel; } public void setRiskLevel(Integer riskLevel) { this.riskLevel = riskLevel; }
    public String getConditionJson() { return conditionJson; } public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }
}
