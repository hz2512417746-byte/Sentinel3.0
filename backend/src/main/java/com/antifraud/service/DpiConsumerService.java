package com.antifraud.service;

import com.antifraud.entity.*;
import com.antifraud.repository.*;
import com.antifraud.entity.BlockRecord;
import com.antifraud.entity.RuleConfig;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import com.antifraud.config.LogWebSocketHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DpiConsumerService {
    // 增块分桶：规则名 → 值 → 计数。按值分桶，阈值变化时直接算高于阈值的桶和
    public static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentSkipListMap<Double, java.util.concurrent.atomic.LongAdder>> RULE_BUCKETS = new java.util.concurrent.ConcurrentHashMap<>();
    public static volatile boolean SEEDED = false;

    private static void recordRuleHit(String ruleName, double actualValue) {
        var map = RULE_BUCKETS.computeIfAbsent(ruleName, k -> new java.util.concurrent.ConcurrentSkipListMap<>());
        map.computeIfAbsent(actualValue, k -> new java.util.concurrent.atomic.LongAdder()).increment();
    }

    /** 快速统计高于阈值的命中数 */
    public static long countAboveThreshold(String ruleName, double threshold) {
        var map = RULE_BUCKETS.get(ruleName);
        if (map == null) return 0;
        return map.tailMap(threshold, true).values().stream().mapToLong(java.util.concurrent.atomic.LongAdder::sum).sum();
    }

    private final LogEventRepository logRepo;
    private final AlertRepository alertRepo;
    private final BlockRecordRepository blockRepo;
    private final SimpMessagingTemplate ws;  // 保留用于告警
    private final LogWebSocketHandler logHandler;  // 原生WS，推送日志+告警
    private final ObjectMapper json = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    public static final List<Map<String,Object>> latencySamples = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_SAMPLES = 300;
    private final List<LogEvent> logBuffer = Collections.synchronizedList(new ArrayList<>());
    private final List<Alert> alertBuffer = Collections.synchronizedList(new ArrayList<>());

    private final RuleService ruleService;
    private final MlScoringService mlScoring;  // SMILE 逻辑回归模型

    private final RedisService redis;

    public DpiConsumerService(LogEventRepository logRepo, AlertRepository alertRepo, BlockRecordRepository blockRepo,
                              SimpMessagingTemplate ws, LogWebSocketHandler logHandler,
                              RuleService ruleService, MlScoringService mlScoring, RedisService redis) {
        this.logRepo = logRepo; this.alertRepo = alertRepo; this.blockRepo = blockRepo;
        this.ws = ws; this.logHandler = logHandler;
        this.ruleService = ruleService; this.mlScoring = mlScoring; this.redis = redis;
    }

    @PostConstruct public void start() {
        new Thread(this::refreshBanned).start();
        new Thread(this::flushLoop).start();
        // 先跑种子，种子完成后才启消费者——避免双重计数
        new Thread(() -> { seedBuckets(); new Thread(this::consume).start(); }).start();
    }

    private void seedBuckets() {
        try {
            System.out.println("[Seed] 开始从DB初始化分桶计数器...");
            int count = 0;
            // 轻量查询：只取hitRules非空的日志的hitRules字段
            var logs = logRepo.findHitRulesForSeeding();
            if (logs == null) { SEEDED = true; return; }
            for (Object[] row : logs) {
                String hr = (String) row[0];
                if (hr == null || hr.isBlank()) continue;
                for (String line : hr.split("\n")) {
                    int idx = line.indexOf("实际=");
                    if (idx < 0) continue;
                    int start = line.indexOf("「");
                    int end = line.indexOf("」");
                    if (start < 0 || end < 0) continue;
                    String ruleName = line.substring(start + 1, end);
                    try {
                        String valStr = line.substring(idx + 3).replaceAll("[^0-9.]", "");
                        if (!valStr.isEmpty()) {
                            recordRuleHit(ruleName, Double.parseDouble(valStr));
                            count++;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            System.out.println("[Seed] 完成，" + count + " 条命中");
            SEEDED = true;
        } catch (Exception e) {
            System.err.println("[Seed] 失败: " + e.getMessage());
            SEEDED = true;
        }
    }

    private void flushLoop() {
        while(true) {
            try { Thread.sleep(500);
                if(!logBuffer.isEmpty()) {
                    List<LogEvent> batch;
                    synchronized(logBuffer) {
                        batch = new ArrayList<>(logBuffer);
                        logBuffer.clear();
                    }
                    logRepo.saveAll(batch);
                }
                if(!alertBuffer.isEmpty()) {
                    List<Alert> batch;
                    synchronized(alertBuffer) {
                        batch = new ArrayList<>(alertBuffer);
                        alertBuffer.clear();
                    }
                    alertRepo.saveAll(batch);
                }
            } catch(Exception e) { System.err.println("[Flush] 写入失败: " + e.getMessage()); e.printStackTrace(); try{Thread.sleep(500);}catch(Exception ex){} }
        }
    }

    /** 每5秒从DB全量同步封号名单到Redis（ADD + REMOVE 双向同步） */
    private void refreshBanned() {
        while(true) {
            try {
                // 1. DB中所有活跃封号用户
                List<BlockRecord> bans = blockRepo.findByActiveTrue();
                Set<String> dbBanUsers = new HashSet<>();
                Set<String> dbInterceptUsers = new HashSet<>();
                for(BlockRecord b : bans) {
                    if("ban".equals(b.getActionType())) dbBanUsers.add(b.getUserId());
                    else dbInterceptUsers.add(b.getUserId());
                }
                // 2. Redis中当前缓存的封号/拦截用户
                Set<String> redisBanned = redis.getBannedUsers();
                Set<String> redisIntercepted = redis.getInterceptedUsers();
                if(redisBanned == null) redisBanned = new HashSet<>();
                if(redisIntercepted == null) redisIntercepted = new HashSet<>();
                // 3. Redis中多余的（DB已解封/解除拦截的）→ 移除
                for(String uid : redisBanned) {
                    if(!dbBanUsers.contains(uid)) redis.unbanUser(uid);
                }
                for(String uid : redisIntercepted) {
                    if(!dbInterceptUsers.contains(uid)) redis.removeIntercept(uid);
                }
                // 4. DB中有但Redis中没有的 → 添加
                for(String uid : dbBanUsers) redis.banUser(uid);
                for(String uid : dbInterceptUsers) redis.interceptUser(uid);
                Thread.sleep(5000);
            } catch(Exception e) { System.err.println("[RefreshBanned] 同步失败: " + e.getMessage()); try{Thread.sleep(5000);}catch(Exception ex){} }
        }
    }

    @SuppressWarnings("unchecked")
    private void consume() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "dpi-backend-" + UUID.randomUUID().toString().substring(0,8));
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "latest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("dpi_enriched"));
            System.out.println("[Consumer] Listening on dpi_enriched...");
            while (true) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(100))) {
                    try {
                        processEntry(json.readValue(r.value(), Map.class));
                    } catch (Exception ex) { System.err.println("[Consumer] Error: " + ex.getMessage()); }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processEntry(Map<String, Object> e) {
        long t0 = System.nanoTime();

        // 封禁防火墙
        String uid = (String) e.getOrDefault("user_id", "");
        if (uid == null || uid.isEmpty() || redis.isBanned(uid)) return;

        // 1. 从 Flink 富化消息中提取特征（ZengKuai 已在 Flink 中计算）
        @SuppressWarnings("unchecked")
        Map<String, Object> rawFeatures = (Map<String, Object>) e.getOrDefault("features", new LinkedHashMap<>());
        Map<String, Double> features = new LinkedHashMap<>();
        for (Map.Entry<String, Object> fe : rawFeatures.entrySet()) {
            features.put(fe.getKey(), fe.getValue() instanceof Number ? ((Number) fe.getValue()).doubleValue() : 0.0);
        }
        long t1 = System.nanoTime();

        // 2. 规则引擎
        double ruleScore = computeRuleScore(features, e);
        long t2 = System.nanoTime();

        // 3. ML打分
        double mlScore = computeMlScore(features, e);
        long t3 = System.nanoTime();

        // 4. 决策
        double riskScore = Math.min(1.0, 0.4 * ruleScore + 0.6 * mlScore);
        String decision = riskScore >= 0.65 ? "block" : riskScore >= 0.45 ? "warn" : "allow";
        int riskLevel = riskScore >= 0.65 ? 3 : riskScore >= 0.45 ? 2 : riskScore >= 0.30 ? 1 : 0;
        long t4 = System.nanoTime();

        // 5. 持久化
        String logId = (String) e.getOrDefault("log_id", UUID.randomUUID().toString());
        String userId = (String) e.getOrDefault("user_id", "");
        String srcIp = (String) e.getOrDefault("src_ip", "");
        String eventType = (String) e.getOrDefault("event_type", "");
        String domain = (String) e.getOrDefault("domain", "");
        String url = (String) e.getOrDefault("url", "");
        String appName = (String) e.getOrDefault("app_name", "");
        String contactPhone = (String) e.getOrDefault("contact_phone", "");
        Integer fraudLabel = "1".equals(String.valueOf(e.get("fraud_label"))) ? 1 : 0;
        String ts = (String) e.get("timestamp");

        // 运营商信令字段（HTTP/HTTPS 信令 + 湖州涉诈分类）
        String msisdn = (String) e.getOrDefault("msisdn", "");
        Integer tac = asInt(e.get("tac"));
        Long cellId = asLong(e.get("cell_id"));
        String lacCi = (String) e.getOrDefault("lac_ci", "");
        Integer appType = asInt(e.get("app_type"));
        Long appSubType = asLong(e.get("app_sub_type"));
        Integer appContent = asInt(e.get("app_content"));
        Integer appStatus = asInt(e.get("app_status"));
        Long dlData = asLong(e.get("dl_data"));
        String serverIp = (String) e.getOrDefault("server_ip", "");
        Integer serverPort = asInt(e.get("server_port"));
        String sni = (String) e.getOrDefault("sni", "");
        String referUri = (String) e.getOrDefault("refer_uri", "");
        String fraudUrlCategory = (String) e.getOrDefault("fraud_url_category", "");

        LogEvent log = new LogEvent();
        log.setLogId(logId); log.setUserId(userId); log.setSrcIp(srcIp);
        log.setEventType(eventType);
        log.setDomain(domain); log.setUrl(url); log.setAppName(appName); log.setContactPhone(contactPhone);
        log.setMsisdn(msisdn); log.setTac(tac); log.setCellId(cellId); log.setLacCi(lacCi);
        log.setAppType(appType); log.setAppSubType(appSubType); log.setAppContent(appContent); log.setAppStatus(appStatus);
        log.setDlData(dlData); log.setServerIp(serverIp); log.setServerPort(serverPort);
        log.setSni(sni); log.setReferUri(referUri); log.setFraudUrlCategory(fraudUrlCategory);
        log.setFraudLabel(fraudLabel); log.setMlScore(mlScore); log.setRuleScore(ruleScore);
        log.setRiskScore(riskScore); log.setRiskLevel(riskLevel); log.setDecision(decision);
        log.setIsBlocked(riskScore >= 0.65);
        // 自动拦截：写入block_records
        if (riskScore >= 0.65) {
            boolean alreadyExists = blockRepo.findByActiveTrue().stream()
                .anyMatch(r -> r.getUserId().equals(userId) && "intercept".equals(r.getActionType()));
            if (!alreadyExists && !redis.isBanned(userId)) {
                BlockRecord intercept = new BlockRecord();
                intercept.setUserId(userId); intercept.setActionType("intercept");
                intercept.setReason("自动拦截-风险"+String.format("%.2f",riskScore));
                intercept.setActive(true);
                blockRepo.save(intercept);
            }
        }
        log.setHitRules(formatRules(features, fraudLabel, e));
        log.setRawJson(e.toString());
        try { log.setTimestamp(ts != null ? LocalDateTime.parse(ts, FMT) : LocalDateTime.now()); }
        catch (Exception x) { log.setTimestamp(LocalDateTime.now()); }
        synchronized(logBuffer){logBuffer.add(log);};
        // 原生 WebSocket 推送日志（type=log）
        try {
            Map<String, Object> wsMsg = new LinkedHashMap<>();
            wsMsg.put("type", "log");
            wsMsg.put("data", log);
            logHandler.broadcast(json.writeValueAsString(wsMsg));
        } catch(Exception ignored) {}
        long t5 = System.nanoTime();

        if (riskLevel >= 1) {
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString()); alert.setLogId(logId);
            alert.setUserId(userId); alert.setAlertLevel(riskLevel); alert.setRiskScore(riskScore);
            alert.setTitle(userId + " - " + eventType);
            alert.setDetail("事件:" + eventType + " | 风险:" + String.format("%.2f", riskScore) + " | " + log.getHitRules().replace("\n", " "));
            alert.setHitRules(log.getHitRules());
            synchronized(alertBuffer){alertBuffer.add(alert);};
            // 原生 WebSocket 推送告警（type=alert）
            try {
                Map<String, Object> wsMsg = new LinkedHashMap<>();
                wsMsg.put("type", "alert");
                wsMsg.put("data", alert);
                logHandler.broadcast(json.writeValueAsString(wsMsg));
            } catch(Exception ignored) {}

            // 自动封禁：月累计≥50次「警告及以上」告警（忽略关注级），数据库端直接计数避免全表扫描
            long monthAlerts = alertRepo.countByUserSince(userId, LocalDateTime.now().minusDays(30));
            if (monthAlerts >= 50 && !redis.isBanned(userId)) {
                BlockRecord ban = new BlockRecord();
                ban.setUserId(userId); ban.setActionType("ban");
                ban.setReason("月累计"+monthAlerts+"次告警，自动封禁");
                ban.setActive(true);
                blockRepo.save(ban);
                redis.banUser(userId);
                // 系统日志——封号事件对前端可见
                LogEvent sysLog = new LogEvent();
                sysLog.setLogId(UUID.randomUUID().toString()); sysLog.setUserId(userId);
                sysLog.setEventType("ban"); sysLog.setDecision("block"); sysLog.setIsBlocked(true);
                sysLog.setRiskScore(1.0); sysLog.setRiskLevel(3);
                sysLog.setHitRules("系统自动封禁: 月累计"+monthAlerts+"次告警");
                sysLog.setTimestamp(LocalDateTime.now());
                logBuffer.add(sysLog);
                System.out.println("[AutoBan] " + userId + " 月累计" + monthAlerts + "次告警，已封禁");
            }
        }

        // 6. 记录延迟 (ns → ms)
        Map<String,Object> sample = new LinkedHashMap<>();
        sample.put("ts", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        // 真实增块延迟：从Flink富化消息中读取zengkuai_ms
        Object zkMs = e.get("zengkuai_ms");
        sample.put("zengkuai_ms", zkMs instanceof Number ? ((Number)zkMs).doubleValue() : 0.0);
        sample.put("rule_ms", Math.round((t2-t1)/1e6*1000)/1000.0);
        sample.put("ml_ms", Math.round((t3-t2)/1e6*1000)/1000.0);
        sample.put("decide_ms", Math.round((t4-t3)/1e6*1000)/1000.0);
        sample.put("db_ms", Math.round((t5-t4)/1e6*1000)/1000.0);
        sample.put("total_ms", Math.round((t5-t0)/1e6*1000)/1000.0 + (zkMs instanceof Number ? ((Number)zkMs).doubleValue() : 0.0));
        sample.put("user", userId); sample.put("risk", Math.round(riskScore*100)/100.0);
        sample.put("decision", decision);
        latencySamples.add(sample);
        if (latencySamples.size() > MAX_SAMPLES) latencySamples.remove(0);
    }

    // ─── 规则引擎：数据库规则（支持 AND 复合条件 + 三级风险） ───
    private double computeRuleScore(Map<String, Double> f, Map<String, Object> e) {
        double score = 0; int hit = 0;
        for (RuleConfig r : ruleService.getEnabled()) {
            try {
                RuleEval ev = evaluateRule(r, f);
                if (ev.hit) {
                    recordRuleHit(r.getName(), ev.value);
                    int lvl = r.getRiskLevel() != null ? r.getRiskLevel() : 2;
                    double lvlFactor = lvl >= 3 ? 1.0 : lvl == 2 ? 0.75 : 0.55; // 高/中/低风险加权
                    score += r.getWeight() * lvlFactor * (0.5 + 0.5 * ev.normValue);
                    hit++;
                }
            } catch(Exception ignored) {}
        }
        return hit > 0 ? Math.min(1.0, score / Math.max(hit, 1)) : 0;
    }

    // ─── ML 评分（SMILE 逻辑回归模型；未训练时自动回退手写加权） ───
    private double computeMlScore(Map<String, Double> f, Map<String, Object> e) {
        return mlScoring.predict(f);
    }

    /** 单条规则命中评估：复杂规则（conditionJson）需所有条件 AND 全命中 */
    private RuleEval evaluateRule(RuleConfig r, Map<String, Double> f) {
        RuleEval ev = new RuleEval();
        List<Condition> conds = parseConditions(r);
        if (!conds.isEmpty()) {
            double ratioSum = 0; boolean allHit = true;
            for (Condition c : conds) {
                double val = f.getOrDefault(c.feature, 0.0);
                if (val < c.threshold) { allHit = false; break; }
                ratioSum += Math.min(1.0, (val - c.threshold) / Math.max(c.threshold, 1.0));
            }
            if (allHit) { ev.hit = true; ev.value = ratioSum / conds.size(); ev.normValue = ev.value; }
        } else {
            double val = f.getOrDefault(r.getFeature(), 0.0);
            if (val >= r.getThreshold() && r.getThreshold() > 0) {
                ev.hit = true; ev.value = val;
                // 归一化到[0,1]：刚达标=0，超阈值越多越接近1（与复杂规则口径一致）
                ev.normValue = Math.min(1.0, (val - r.getThreshold()) / Math.max(r.getThreshold(), 1.0));
            }
        }
        return ev;
    }

    @SuppressWarnings("unchecked")
    private List<Condition> parseConditions(RuleConfig r) {
        List<Condition> list = new ArrayList<>();
        String cj = r.getConditionJson();
        if (cj == null || cj.isBlank()) return list;
        try {
            Map<String, Object> root = json.readValue(cj, Map.class);
            Object conds = root.get("conditions");
            if (conds instanceof List) {
                for (Object o : (List<Object>) conds) {
                    if (o instanceof Map) {
                        Map<String, Object> cm = (Map<String, Object>) o;
                        String feat = String.valueOf(cm.get("feature"));
                        double thr = cm.get("threshold") instanceof Number ? ((Number) cm.get("threshold")).doubleValue() : 0;
                        if (feat != null && !feat.isBlank()) list.add(new Condition(feat, thr));
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private String formatRules(Map<String, Double> f, int fraud, Map<String, Object> e) {
        StringBuilder sb = new StringBuilder();
        // 从 rule_configs 动态生成命中展示，与规则引擎实际触发保持一致
        for (RuleConfig r : ruleService.getEnabled()) {
            try {
                RuleEval ev = evaluateRule(r, f);
                if (ev.hit) {
                    sb.append("触发「").append(r.getName()).append("」实际=")
                      .append(ev.value == Math.floor(ev.value) ? String.valueOf((long) ev.value) : String.format("%.2f", ev.value))
                      .append("\n");
                }
            } catch (Exception ignored) {}
        }
        if (fraud == 1 && e.containsKey("ip_risk")) sb.append("触发「IP风险」\n");
        return sb.toString();
    }

    /** 复合条件项 */
    private static class Condition { final String feature; final double threshold; Condition(String f, double t){ this.feature=f; this.threshold=t; } }
    /** 规则命中结果 */
    private static class RuleEval { boolean hit; double value; double normValue; }

    // ─── 数值字段安全读取（JSON 反序列化后可能是 Integer/Long/String） ───
    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception ex) { return null; }
    }
    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception ex) { return null; }
    }
}
