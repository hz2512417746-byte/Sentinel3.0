package com.antifraud.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "block_records")
public class BlockRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String userId;

    @Column(nullable = false, length = 16)
    private String actionType; // intercept / ban

    @Column(length = 256)
    private String reason;

    private LocalDateTime createdAt = LocalDateTime.now();
    private Boolean active = true;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; } public void setUserId(String userId) { this.userId = userId; }
    public String getActionType() { return actionType; } public void setActionType(String actionType) { this.actionType = actionType; }
    public String getReason() { return reason; } public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getActive() { return active; } public void setActive(Boolean active) { this.active = active; }
}
