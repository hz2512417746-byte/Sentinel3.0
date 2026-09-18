package com.antifraud.service;

import com.antifraud.entity.*;
import com.antifraud.repository.*;
import com.antifraud.entity.BlockRecord;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DpiConsumerService {
    private final LogEventRepository logRepo;
    private final AlertRepository alertRepo;
    private final BlockRecordRepository blockRepo;
    private final SimpMessagingTemplate ws;
    private final ObjectMapper json = new ObjectMapper();
    private final ZengKuaiEngine zengKuai = new ZengKuaiEngine();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    public static final List<Map<String,Object>> latencySamples = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_SAMPLES = 300;
    private volatile Set<String> bannedUsers = new HashSet<>();
    private final List<LogEvent> logBuffer = Collections.synchronizedList(new ArrayList<>());
    private final List<Alert> alertBuffer = Collections.synchronizedList(new ArrayList<>());

    public DpiConsumerService(LogEventRepository logRepo, AlertRepository alertRepo, BlockRecordRepository blockRepo, SimpMessagingTemplate ws) {
        this.logRepo = logRepo; this.alertRepo = alertRepo; this.blockRepo = blockRepo; this.ws = ws;
    }

    @PostConstruct public void start() {
        new Thread(this::refreshBanned).start();
        new Thread(this::consume).start();
        new Thread(this::flushLoop).start();
    }

    private void flushLoop() {
        while(true) {
            try { Thread.sleep(500);
                if(!logBuffer.isEmpty()) {
                    List<LogEvent> batch = new ArrayList<>(logBuffer);
                    logBuffer.clear();
                    logRepo.saveAll(batch);
                }
                if(!alertBuffer.isEmpty()) {
                    List<Alert> batch = new ArrayList<>(alertBuffer);
                    alertBuffer.clear();
                    alertRepo.saveAll(batch);
                }
            } catch(Exception e) { try{Thread.sleep(500);}catch(Exception ex){} }
        }
    }

    /** 每5秒从DB刷新封号名单 */
    private void refreshBanned() {
        while(true) {
            try {
                List<BlockRecord> bans = blockRepo.findByActiveTrue();
                Set<String> set = new HashSet<>();
                for(BlockRecord b : bans) {
                    if("ban".equals(b.getActionType())) set.add(b.getUserId());
                }
                bannedUsers = set;
                Thread.sleep(5000);
            } catch(Exception e) { try{Thread.sleep(5000);}catch(Exception ex){} }
        }
    }

    @SuppressWarnings("unchecked")
    private void consume() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "dpi-backend");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "latest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("dpi_logs"));
            System.out.println("[Consumer] Listening on dpi_logs...");
            while (true) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(100))) {
                    try {
                        processEntry(json.readValue(r.value(), Map.class));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processEntry(Map<String, Object> e) {
        long t0 = System.nanoTime();

        // 封禁防火墙：已封号用户DPI直接丢弃，不落库、不推送、不告警
        String uid = (String) e.getOrDefault("user_id", "");
        if (bannedUsers.contains(uid)) return;

        // 1. 增块特征计算
        zengKuai.process(e);
        Map<String, Double> features = zengKuai.getFeatures(e);
        long t1 = System.nanoTime();

        // 2. 规则引擎
        double ruleScore = computeRuleScore(features, e);
        long t2 = System.nanoTime();

        // 3. ML打分
        double mlScore = computeMlScore(features, e);
        long t3 = System.nanoTime();

        // 4. 决策
        double riskScore = Math.min(1.0, 0.3 * ruleScore + 0.5 * mlScore);
        String decision = riskScore >= 0.7 ? "block" : riskScore >= 0.5 ? "warn" : "allow";
        int riskLevel = riskScore >= 0.7 ? 3 : riskScore >= 0.5 ? 2 : 0;
        long t4 = System.nanoTime();

        // 5. 持久化
        String logId = (String) e.getOrDefault("log_id", UUID.randomUUID().toString());
        String userId = (String) e.getOrDefault("user_id", "");
        String srcIp = (String) e.getOrDefault("src_ip", "");
        String eventType = (String) e.getOrDefault("event_type", "");
        Number amount = (Number) e.get("amount");
        Integer fraudLabel = (Integer) e.getOrDefault("fraud_label", 0);
        String ts = (String) e.get("timestamp");

        LogEvent log = new LogEvent();
        log.setLogId(logId); log.setUserId(userId); log.setSrcIp(srcIp);
        log.setEventType(eventType); log.setAmount(amount != null ? amount.doubleValue() : null);
        log.setFraudLabel(fraudLabel); log.setMlScore(mlScore); log.setRuleScore(ruleScore);
        log.setRiskScore(riskScore); log.setRiskLevel(riskLevel); log.setDecision(decision);
        log.setIsBlocked(riskScore >= 0.7);
        // 自动拦截：写入block_records
        if (riskScore >= 0.7) {
            boolean alreadyExists = blockRepo.findByActiveTrue().stream()
                .anyMatch(r -> r.getUserId().equals(userId) && "intercept".equals(r.getActionType()));
            if (!alreadyExists && !bannedUsers.contains(userId)) {
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
        logBuffer.add(log);
        long t5 = System.nanoTime();

        // 告警：仅高风名单用户生成（fraud_label==1），其他用户即使风险分高也不告警
        boolean isHighRiskUser = fraudLabel != null && fraudLabel == 1;
        if (riskLevel >= 2 && isHighRiskUser) {
            Alert alert = new Alert();
            alert.setAlertId(UUID.randomUUID().toString()); alert.setLogId(logId);
            alert.setUserId(userId); alert.setAlertLevel(riskLevel); alert.setRiskScore(riskScore);
            alert.setTitle(userId + " - " + eventType);
            alert.setDetail("事件:" + eventType + " | 风险:" + String.format("%.2f", riskScore) + " | " + log.getHitRules().replace("\n", " "));
            alert.setHitRules(log.getHitRules());
            alertBuffer.add(alert);
            ws.convertAndSend("/topic/alerts", alert);

            // 自动封禁：月累计≥5次告警
            long monthAlerts = alertRepo.findAll().stream()
                .filter(a -> a.getUserId().equals(userId) && a.getTimestamp().isAfter(LocalDateTime.now().minusDays(30)))
                .count();
            if (monthAlerts >= 5 && !bannedUsers.contains(userId)) {
                BlockRecord ban = new BlockRecord();
                ban.setUserId(userId); ban.setActionType("ban");
                ban.setReason("月累计"+monthAlerts+"次告警，自动封禁");
                ban.setActive(true);
                blockRepo.save(ban);
                bannedUsers.add(userId);
                System.out.println("[AutoBan] " + userId + " 月累计" + monthAlerts + "次告警，已封禁");
            }
        }

        // 6. 记录延迟 (ns → ms)
        Map<String,Object> sample = new LinkedHashMap<>();
        sample.put("ts", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        sample.put("zengkuai_ms", Math.round((t1-t0)/1e6*1000)/1000.0);
        sample.put("rule_ms", Math.round((t2-t1)/1e6*1000)/1000.0);
        sample.put("ml_ms", Math.round((t3-t2)/1e6*1000)/1000.0);
        sample.put("decide_ms", Math.round((t4-t3)/1e6*1000)/1000.0);
        sample.put("db_ms", Math.round((t5-t4)/1e6*1000)/1000.0);
        sample.put("total_ms", Math.round((t5-t0)/1e6*1000)/1000.0);
        sample.put("user", userId); sample.put("risk", Math.round(riskScore*100)/100.0);
        sample.put("decision", decision);
        latencySamples.add(sample);
        if (latencySamples.size() > MAX_SAMPLES) latencySamples.remove(0);
    }

    // ─── 规则引擎 ───
    private double computeRuleScore(Map<String, Double> f, Map<String, Object> e) {
        double s = 0; double w = 0;
        if (f.getOrDefault("user_failed_login_15m", 0.0) >= 2) { s += 1.0; w += 1.0; }
        if (f.getOrDefault("user_transfer_amount_1h", 0.0) >= 50000) { s += 1.2; w += 1.2; }
        if (f.getOrDefault("ip_unique_users_24h", 0.0) >= 3) { s += 1.0; w += 1.0; }
        if (f.getOrDefault("device_unique_users_24h", 0.0) >= 3) { s += 1.0; w += 1.0; }
        if (f.getOrDefault("receiver_transfer_count_1h", 0.0) >= 5) { s += 1.2; w += 1.2; }
        return w > 0 ? s / w : 0;
    }

    // ─── ML打分 (特征加权) ───
    private double computeMlScore(Map<String, Double> f, Map<String, Object> e) {
        double s = 0, w = 0;
        if (f.getOrDefault("user_failed_login_15m", 0.0) >= 2) { s += 0.15; w += 0.15; }
        if (f.getOrDefault("user_transfer_amount_1h", 0.0) >= 50000) { s += 0.12; w += 0.12; }
        if (f.getOrDefault("ip_unique_users_24h", 0.0) >= 3) { s += 0.12; w += 0.12; }
        if (f.getOrDefault("device_unique_users_24h", 0.0) >= 3) { s += 0.12; w += 0.12; }
        if (f.getOrDefault("receiver_transfer_count_1h", 0.0) >= 5) { s += 0.12; w += 0.12; }
        return w > 0 ? Math.min(1.0, s / w) : 0;
    }

    private String formatRules(Map<String, Double> f, int fraud, Map<String, Object> e) {
        StringBuilder sb = new StringBuilder();
        if (f.getOrDefault("user_failed_login_15m", 0.0) >= 2)
            sb.append("触发「高频登录失败」实际=").append(f.get("user_failed_login_15m").intValue()).append("次\n");
        if (f.getOrDefault("user_transfer_amount_1h", 0.0) >= 50000)
            sb.append("触发「大额转账」实际=¥").append(String.format("%.0f", f.get("user_transfer_amount_1h"))).append("\n");
        if (f.getOrDefault("ip_unique_users_24h", 0.0) >= 3)
            sb.append("触发「IP关联多用户」实际=").append(f.get("ip_unique_users_24h").intValue()).append("人\n");
        if (f.getOrDefault("device_unique_users_24h", 0.0) >= 3)
            sb.append("触发「设备关联多用户」实际=").append(f.get("device_unique_users_24h").intValue()).append("人\n");
        if (f.getOrDefault("receiver_transfer_count_1h", 0.0) >= 5)
            sb.append("触发「收款方快进快出」实际=").append(f.get("receiver_transfer_count_1h").intValue()).append("笔\n");
        if (fraud == 1 && e.containsKey("ip_risk")) sb.append("触发「IP风险」\n");
        return sb.toString();
    }
}
