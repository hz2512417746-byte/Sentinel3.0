package com.antifraud.controller;

import com.antifraud.entity.RuleConfig;
import com.antifraud.repository.LogEventRepository;
import com.antifraud.service.DpiConsumerService;
import com.antifraud.service.RuleService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/rules")
public class RuleController {
    private final RuleService ruleService;
    private final LogEventRepository logRepo;

    public RuleController(RuleService ruleService, LogEventRepository logRepo) {
        this.ruleService = ruleService; this.logRepo = logRepo;
    }

    @GetMapping
    public List<RuleConfig> getAll() { return ruleService.getAllRules(); }

    @GetMapping("/active")
    public List<Map<String, Object>> getActive() { return ruleService.getActiveRules(); }

    @PostMapping
    public RuleConfig add(@RequestBody RuleConfig rule) { return ruleService.addRule(rule); }

    @PutMapping("/{id}")
    public RuleConfig update(@PathVariable Long id, @RequestBody RuleConfig rule) {
        return ruleService.updateRule(id, rule);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        ruleService.deleteRule(id);
        return Map.of("status", "ok");
    }

    @PostMapping("/{id}/toggle")
    public Map<String, String> toggle(@PathVariable Long id) {
        ruleService.toggleRule(id);
        return Map.of("status", "ok");
    }

    @PostMapping("/reload")
    public Map<String, String> reload() {
        ruleService.reload();
        return Map.of("status", "reloaded");
    }

    @GetMapping("/preview")
    public Map<String, Object> preview(@RequestParam String ruleName,
                                        @RequestParam(defaultValue = "0") int threshold) {
        long total = logRepo.count();
        if (total == 0) total = 1; // 防止除零
        long hit = 0;
        if (DpiConsumerService.SEEDED) {
            hit = DpiConsumerService.countAboveThreshold(ruleName, threshold);
        }
        return Map.of("ruleName", ruleName, "threshold", threshold, "hitCount", hit, "total", total,
            "ready", DpiConsumerService.SEEDED);
    }
}
