package com.antifraud.controller;

import com.antifraud.entity.LogEvent;
import com.antifraud.repository.LogEventRepository;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class LogController {
    private final LogEventRepository logRepo;

    public LogController(LogEventRepository logRepo) { this.logRepo = logRepo; }

    @GetMapping("/logs")
    public Page<LogEvent> getLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String srcIp,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Integer riskLevel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return logRepo.search(userId, srcIp, eventType, riskLevel,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "timestamp")));
    }

    @GetMapping("/logs/{logId}")
    public LogEvent getLog(@PathVariable String logId) {
        return logRepo.findById(logId).orElse(null);
    }
}
