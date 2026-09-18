package com.antifraud.controller;

import com.antifraud.entity.Alert;
import com.antifraud.repository.*;
import com.antifraud.service.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api")
public class DashboardController {
    private final DashboardService dashboardService;
    private final AlertRepository alertRepo;
    private final LogEventRepository logRepo;
    private final BlockRecordRepository blockRepo;

    public DashboardController(DashboardService dashboardService, AlertRepository alertRepo,
                               LogEventRepository logRepo, BlockRecordRepository blockRepo) {
        this.dashboardService = dashboardService; this.alertRepo = alertRepo;
        this.logRepo = logRepo; this.blockRepo = blockRepo;
    }

    @GetMapping("/alerts")
    public List<com.antifraud.entity.Alert> getAlerts(@RequestParam(defaultValue = "pending") String status,
                                  @RequestParam(defaultValue = "50") int pageSize) {
        return alertRepo.findByStatusOrderByTimestampDesc(status).stream().limit(pageSize).toList();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> s = dashboardService.getStats();
        s.put("zengkuaiBuckets", DpiConsumerService.latencySamples.size());
        return s;
    }

    @GetMapping("/alerts/history")
    public Map<String,Object> alertHistory(@RequestParam(defaultValue="week") String range,
                                            @RequestParam(required=false) String userId,
                                            @RequestParam(defaultValue="1") int page,
                                            @RequestParam(defaultValue="30") int pageSize) {
        LocalDateTime since = LocalDateTime.now().minusDays("week".equals(range)?7:"month".equals(range)?30:365);
        // 数据库端分页+过滤，替代 findAll() 全表扫描
        var pageable = org.springframework.data.domain.PageRequest.of(page-1, pageSize);
        List<Alert> filtered;
        if (userId != null && !userId.isEmpty()) {
            filtered = alertRepo.findByUserAndSince(userId, since, pageable);
        } else {
            filtered = alertRepo.findByTimestampAfterOrderByTimestampDesc(since, pageable);
        }
        long total = (userId != null && !userId.isEmpty())
            ? alertRepo.countByUserAndSince(userId, since)
            : alertRepo.countByTimestampAfter(since);
        var list = filtered.stream().map(a->{
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("alertId",a.getAlertId()); m.put("userId",a.getUserId());
            m.put("alertLevel",a.getAlertLevel()); m.put("riskScore",a.getRiskScore());
            m.put("title",a.getTitle()); m.put("detail",a.getDetail());
            m.put("timestamp",a.getTimestamp().toString());
            return m;
        }).toList();
        return Map.of("content", list, "totalElements", total, "page", page, "pageSize", pageSize);
    }

    @GetMapping("/alerts/frequency")
    public Map<String, Long> alertFrequency() {
        LocalDateTime now = LocalDateTime.now();
        long last1h = alertRepo.countByTimestampAfter(now.minusHours(1));
        long last24h = alertRepo.countByTimestampAfter(now.minusHours(24));
        long last7d = alertRepo.countByTimestampAfter(now.minusDays(7));
        return Map.of("1h", last1h, "24h", last24h, "7d", last7d);
    }

    @GetMapping("/pipeline/metrics")
    public Map<String, Object> pipelineMetrics() {
        var samples = new ArrayList<>(DpiConsumerService.latencySamples);
        if (samples.isEmpty()) return Map.of("samples", List.of(), "stats", Map.of());
        String[] stages = {"zengkuai_ms","rule_ms","ml_ms","decide_ms","db_ms","total_ms"};
        Map<String,Double> avgs = new LinkedHashMap<>();
        for (String st : stages) {
            double avg = samples.stream().mapToDouble(s -> ((Number)s.getOrDefault(st,0)).doubleValue()).average().orElse(0);
            avgs.put("avg_"+st, Math.round(avg*1000)/1000.0);
        }
        Map<String,Object> stats = new LinkedHashMap<>(avgs);
        stats.put("samples", samples.size());
        int from = Math.max(0, samples.size()-60);
        return Map.of("samples", samples.subList(from, samples.size()), "stats", stats);
    }

    @GetMapping("/users/{userId}/profile")
    public Map<String, Object> userProfile(@PathVariable String userId) {
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 500);
        List<com.antifraud.entity.LogEvent> logs = logRepo.findByUserIdOrderByTimestampDesc(userId, pageable);
        List<com.antifraud.entity.LogEvent> recent24h = logs.stream()
            .filter(l -> l.getTimestamp() != null && l.getTimestamp().isAfter(since24h)).toList();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", userId); p.put("totalLogs24h", recent24h.size());
        p.put("events", recent24h.stream().collect(Collectors.groupingBy(
            l -> l.getEventType() != null ? l.getEventType() : "unknown", Collectors.counting())));
        long fraudEvents = recent24h.stream().filter(l -> l.getEventType() != null &&
            (l.getEventType().equals("visit_fraud_url") || l.getEventType().equals("fraud_call") ||
             l.getEventType().equals("fraud_sms") || l.getEventType().equals("download_app") ||
             l.getEventType().equals("screen_share") || l.getEventType().equals("abnormal_overseas"))).count();
        p.put("fraudEvents24h", fraudEvents);
        p.put("uniqueIps", recent24h.stream().map(com.antifraud.entity.LogEvent::getSrcIp).filter(Objects::nonNull).distinct().count());
        p.put("avgRiskScore", Math.round(recent24h.stream().filter(l -> l.getRiskScore() != null)
            .mapToDouble(com.antifraud.entity.LogEvent::getRiskScore).average().orElse(0) * 1000) / 1000.0);
        p.put("blockedCount", recent24h.stream().filter(l -> l.getIsBlocked() != null && l.getIsBlocked()).count());
        Set<String> rules = new LinkedHashSet<>();
        for (var l : recent24h) {
            if (l.getHitRules() != null && !l.getHitRules().isBlank())
                for (String line : l.getHitRules().split("\n"))
                    if (!line.isBlank()) rules.add(line.trim());
        }
        p.put("hitRules", new ArrayList<>(rules).stream().limit(10).toList());
        p.put("isBanned", blockRepo.findByActiveTrue().stream()
            .anyMatch(b -> "ban".equals(b.getActionType()) && b.getUserId().equals(userId)));
        p.put("alertCount", recent24h.stream().filter(l -> l.getRiskLevel() != null && l.getRiskLevel() >= 2).count());
        return p;
    }
}
