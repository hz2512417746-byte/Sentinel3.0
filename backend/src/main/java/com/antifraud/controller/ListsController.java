package com.antifraud.controller;

import com.antifraud.entity.*;
import com.antifraud.repository.*;
import com.antifraud.service.RedisService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/api/lists")
public class ListsController {
    private final BlockRecordRepository blockRepo;
    private final LogEventRepository logRepo;
    private final AlertRepository alertRepo;
    private final RedisService redis;

    public ListsController(BlockRecordRepository blockRepo, LogEventRepository logRepo, AlertRepository alertRepo, RedisService redis) {
        this.blockRepo = blockRepo; this.logRepo = logRepo; this.alertRepo = alertRepo; this.redis = redis;
    }

    @GetMapping
    public Map<String, Object> getLists() {
        List<BlockRecord> active = blockRepo.findByActiveTrue();
        Set<String> interceptUsers = new HashSet<>(), banUsers = new HashSet<>();
        for (BlockRecord r : active) {
            if ("ban".equals(r.getActionType())) banUsers.add(r.getUserId());
            else interceptUsers.add(r.getUserId());
        }
        // 封号是最高层操作：已封号用户不出现在拦截名单
        interceptUsers.removeAll(banUsers);
        List<String> flagged = logRepo.findFlaggedUsers();
        flagged.removeAll(interceptUsers); flagged.removeAll(banUsers);
        return Map.of(
            "interceptUsers", new ArrayList<>(interceptUsers),
            "banUsers", new ArrayList<>(banUsers),
            "flaggedUsers", flagged.subList(0, Math.min(50, flagged.size()))
        );
    }

    @GetMapping("/history")
    public Map<String, Object> getHistory(@RequestParam(defaultValue = "week") String range) {
        LocalDateTime since = LocalDateTime.now().minusDays("week".equals(range)?7:"month".equals(range)?30:365);
        List<BlockRecord> records = blockRepo.findSince(since);
        List<Map<String, Object>> result = new ArrayList<>();
        for (BlockRecord r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", r.getUserId());
            m.put("action", r.getActionType());
            m.put("reason", r.getReason());
            m.put("time", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            m.put("active", r.getActive());
            result.add(m);
        }
        return Map.of("total", result.size(), "records", result);
    }

    @Transactional
    @PostMapping("/ban/{userId}")
    public Map<String, String> ban(@PathVariable String userId) {
        // 封号后清除该用户所有待处理告警（拦截历史不动）
        blockRepo.deactivateByUserAndType(userId, "intercept");
        BlockRecord ban = new BlockRecord();
        ban.setUserId(userId); ban.setActionType("ban"); ban.setReason("手动封号"); ban.setActive(true);
        blockRepo.save(ban); redis.banUser(userId);
        // 系统日志
        LogEvent sysLog = new LogEvent();
        sysLog.setLogId(java.util.UUID.randomUUID().toString()); sysLog.setUserId(userId);
        sysLog.setEventType("ban"); sysLog.setDecision("block"); sysLog.setIsBlocked(true);
        sysLog.setRiskScore(1.0); sysLog.setRiskLevel(3); sysLog.setHitRules("管理员手动封号");
        sysLog.setTimestamp(java.time.LocalDateTime.now());
        logRepo.save(sysLog);
        return Map.of("status", "ok");
    }

    @PostMapping("/unban/{userId}")
    public Map<String, String> unban(@PathVariable String userId) {
        blockRepo.deactivateByUser(userId); redis.unbanUser(userId);
        return Map.of("status", "ok");
    }

    @PostMapping("/intercept/{userId}")
    public Map<String, String> intercept(@PathVariable String userId) {
        BlockRecord r = new BlockRecord();
        r.setUserId(userId); r.setActionType("intercept"); r.setReason("手动拦截"); r.setActive(true);
        blockRepo.save(r); redis.interceptUser(userId);
        return Map.of("status", "ok");
    }

    @PostMapping("/unintercept/{userId}")
    public Map<String, String> unintercept(@PathVariable String userId) {
        blockRepo.deactivateByUserAndType(userId, "intercept"); redis.removeIntercept(userId);
        return Map.of("status", "ok");
    }
}
