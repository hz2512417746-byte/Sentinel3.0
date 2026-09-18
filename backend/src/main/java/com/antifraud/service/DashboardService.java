package com.antifraud.service;

import com.antifraud.repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DashboardService {
    private final LogEventRepository logRepo;
    private final AlertRepository alertRepo;

    public DashboardService(LogEventRepository logRepo, AlertRepository alertRepo) {
        this.logRepo = logRepo; this.alertRepo = alertRepo;
    }

    public Map<String, Object> getStats() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalLogs", logRepo.count());
        stats.put("logs1h", logRepo.countByTimestampAfter(now.minusHours(1)));
        stats.put("logs24h", logRepo.countByTimestampAfter(now.minusHours(24)));
        stats.put("fraudCount", logRepo.countByFraudLabel(1));
        stats.put("highRisk", logRepo.countByRiskLevelGreaterThanEqual(2));
        stats.put("blocked", logRepo.countByIsBlockedTrue());
        stats.put("pendingAlerts", alertRepo.countByStatus("pending"));
        // 三态决策分布（block/warn/allow），供首页决策饼图展示真实「阻断/预警/放行」
        Map<String, Object> decisionDistribution = new LinkedHashMap<>();
        decisionDistribution.put("allow", logRepo.countByDecision("allow"));
        decisionDistribution.put("warn", logRepo.countByDecision("warn"));
        decisionDistribution.put("block", logRepo.countByDecision("block"));
        stats.put("decisionDistribution", decisionDistribution);
        return stats;
    }
}
