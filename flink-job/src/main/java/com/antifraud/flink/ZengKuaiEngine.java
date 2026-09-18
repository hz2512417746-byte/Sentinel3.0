package com.antifraud.flink;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时间分桶增量特征计算引擎（网络侧 DPI 特征）
 */
public class ZengKuaiEngine {
    private final Map<String, TimeBucket> store = new ConcurrentHashMap<>();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
    private volatile int processCount = 0;
    private static final int CLEANUP_INTERVAL = 10000; // 每1万条清理一次过期桶（24h以上）

    /** 处理单条日志 → 更新所有相关桶 */
    public void process(Map<String, Object> entry) {
        String mk = minuteKey();
        String event = (String) entry.getOrDefault("event_type", "");
        String user = (String) entry.getOrDefault("user_id", "");
        String ip = (String) entry.getOrDefault("src_ip", "");
        String device = (String) entry.getOrDefault("device_id", "");
        String status = (String) entry.getOrDefault("event_status", "success");
        String cell = String.valueOf(entry.getOrDefault("cell_id", ""));
        String city = (String) entry.getOrDefault("geo_city", "");
        String category = (String) entry.getOrDefault("fraud_url_category", "");
        long traffic = 0;
        Object dl = entry.get("dl_data");
        if (dl instanceof Number n) traffic = n.longValue();
        // 凌晨 0-5 点视为异常时段
        int hour = LocalDateTime.now().getHour();
        boolean abnormalHour = hour >= 0 && hour < 5;

        if (!user.isEmpty()) {
            updateBucket("user:" + user + ":" + event, mk, status, user, ip, device, cell, city, traffic);
            updateBucket("user:" + user + ":*", mk, status, user, ip, device, cell, city, traffic);
            if (abnormalHour) updateBucket("user:" + user + ":abnormal_hour", mk, status, user, ip, device, cell, city, traffic);
            // 涉诈网址分类细分桶（事件+分类，如 visit_fraud_url:色情交友）
            if (!category.isEmpty()) {
                updateBucket("user:" + user + ":" + event + ":" + category, mk, status, user, ip, device, cell, city, traffic);
            }
        }
        if (!ip.isEmpty()) {
            updateBucket("ip:" + ip + ":*", mk, "", user, ip, device, cell, city, traffic);
        }
        if (!device.isEmpty()) {
            updateBucket("device:" + device + ":*", mk, "", user, "", device, cell, city, traffic);
        }
        updateBucket("global:*", mk, "", "", "", "", cell, city, traffic);
        if (++processCount % CLEANUP_INTERVAL == 0) cleanup();
    }

    /** 清理超过24小时的过期桶，防止内存泄漏 */
    private void cleanup() {
        String cutoff = LocalDateTime.now().minusHours(24).format(FMT);
        store.keySet().removeIf(k -> {
            String mk = k.substring(k.lastIndexOf(':') + 1);
            return mk.compareTo(cutoff) < 0;
        });
    }

    private void updateBucket(String prefix, String mk, String status, String user, String ip, String device,
                              String cell, String city, long traffic) {
        String key = prefix + ":" + mk;
        TimeBucket b = store.computeIfAbsent(key, k -> new TimeBucket());
        b.count++;
        if ("failed".equals(status)) b.countFailed++;
        if (!ip.isEmpty()) b.ips.add(ip);
        if (!user.isEmpty()) b.users.add(user);
        if (!device.isEmpty()) b.devices.add(device);
        if (!cell.isEmpty()) b.cells.add(cell);
        if (!city.isEmpty()) b.cities.add(city);
        b.traffic += traffic;
    }

    /** 获取特征向量（27 维网络侧 DPI 特征，与 FeatureVector.ORDER 对齐） */
    public Map<String, Double> getFeatures(Map<String, Object> entry) {
        Map<String, Double> f = new LinkedHashMap<>();
        String user = (String) entry.getOrDefault("user_id", "");
        String ip = (String) entry.getOrDefault("src_ip", "");
        String device = (String) entry.getOrDefault("device_id", "");

        if (!user.isEmpty()) {
            TimeBucket u24h = merge("user:" + user + ":*", 1440);
            f.put("user_login_count_1h", (double) merge("user:" + user + ":login", 60).count);
            f.put("user_login_count_24h", (double) merge("user:" + user + ":login", 1440).count);
            f.put("user_failed_login_15m", (double) merge("user:" + user + ":login", 15).countFailed);
            f.put("user_fraud_url_count_1h", (double) merge("user:" + user + ":visit_fraud_url", 60).count);
            f.put("user_fraud_url_count_24h", (double) merge("user:" + user + ":visit_fraud_url", 1440).count);
            f.put("user_suspicious_domain_count_24h", (double) merge("user:" + user + ":visit_suspicious_domain", 1440).count);
            f.put("user_app_download_count_24h", (double) merge("user:" + user + ":download_app", 1440).count);
            f.put("user_fraud_call_count_24h", (double) merge("user:" + user + ":fraud_call", 1440).count);
            f.put("user_fraud_sms_count_24h", (double) merge("user:" + user + ":fraud_sms", 1440).count);
            f.put("user_overseas_access_count_24h", (double) merge("user:" + user + ":abnormal_overseas", 1440).count);
            f.put("abnormal_hour_access_count_24h", (double) merge("user:" + user + ":abnormal_hour", 1440).count);
            f.put("user_unique_ips_24h", (double) u24h.ips.size());
            f.put("user_unique_devices_24h", (double) u24h.devices.size());
            f.put("user_screen_share_count_24h", (double) merge("user:" + user + ":screen_share", 1440).count);
            // 新增 10 维（银行/会议 App 行为 + 湖州涉诈分类细分 + 位置/流量画像）
            f.put("user_bank_app_count_1h", (double) merge("user:" + user + ":visit_bank_app", 60).count);
            f.put("user_bank_app_count_24h", (double) merge("user:" + user + ":visit_bank_app", 1440).count);
            f.put("user_meeting_app_count_24h", (double) merge("user:" + user + ":visit_meeting_app", 1440).count);
            f.put("user_dating_app_count_24h", (double) merge("user:" + user + ":visit_fraud_url:色情交友", 1440).count);
            f.put("user_gambling_url_count_24h", (double) merge("user:" + user + ":visit_fraud_url:色情博彩", 1440).count);
            f.put("user_shopping_url_count_24h", (double) merge("user:" + user + ":visit_fraud_url:虚假购物", 1440).count);
            f.put("user_illegal_download_count_24h", (double) merge("user:" + user + ":download_app:非法分发", 1440).count);
            f.put("user_traffic_bytes_24h", (double) u24h.traffic);
            f.put("user_unique_cells_24h", (double) u24h.cells.size());
            f.put("user_unique_cities_24h", (double) u24h.cities.size());
            // 规则引擎窗口特征（35min / 120min，供湖州复杂规则 AND 判定，不计入 ML 27 维）
            f.put("user_fraud_url_count_35m", (double) merge("user:" + user + ":visit_fraud_url", 35).count);
            f.put("user_fraud_url_count_120m", (double) merge("user:" + user + ":visit_fraud_url", 120).count);
            f.put("user_bank_app_count_35m", (double) merge("user:" + user + ":visit_bank_app", 35).count);
            f.put("user_bank_app_count_120m", (double) merge("user:" + user + ":visit_bank_app", 120).count);
            f.put("user_dating_app_count_35m", (double) merge("user:" + user + ":visit_fraud_url:色情交友", 35).count);
            f.put("user_dating_app_count_120m", (double) merge("user:" + user + ":visit_fraud_url:色情交友", 120).count);
            f.put("user_gambling_url_count_35m", (double) merge("user:" + user + ":visit_fraud_url:色情博彩", 35).count);
            f.put("user_gambling_url_count_120m", (double) merge("user:" + user + ":visit_fraud_url:色情博彩", 120).count);
            f.put("user_shopping_url_count_35m", (double) merge("user:" + user + ":visit_fraud_url:虚假购物", 35).count);
            f.put("user_shopping_url_count_120m", (double) merge("user:" + user + ":visit_fraud_url:虚假购物", 120).count);
            f.put("user_illegal_download_count_35m", (double) merge("user:" + user + ":download_app:非法分发", 35).count);
            f.put("user_illegal_download_count_120m", (double) merge("user:" + user + ":download_app:非法分发", 120).count);
        }
        if (!ip.isEmpty()) {
            TimeBucket i1h = merge("ip:" + ip + ":*", 60);
            TimeBucket i24h = merge("ip:" + ip + ":*", 1440);
            f.put("ip_event_count_1h", (double) i1h.count);
            f.put("ip_unique_users_24h", (double) i24h.users.size());
        }
        if (!device.isEmpty()) {
            f.put("device_unique_users_24h", (double) merge("device:" + device + ":*", 1440).users.size());
        }
        return f;
    }

    private TimeBucket merge(String prefix, int minutes) {
        TimeBucket result = new TimeBucket();
        for (int i = 0; i < minutes; i++) {
            String key = prefix + ":" + minuteKey(-i);
            TimeBucket b = store.get(key);
            if (b != null) result.merge(b);
        }
        return result;
    }

    public int bucketCount() { return store.size(); }

    private static String minuteKey() { return LocalDateTime.now().format(FMT); }
    private static String minuteKey(int offset) { return LocalDateTime.now().minusMinutes(-offset).format(FMT); }

    static class TimeBucket {
        int count, countFailed;
        long traffic;
        Set<String> ips = new HashSet<>(), users = new HashSet<>(), devices = new HashSet<>();
        Set<String> cells = new HashSet<>(), cities = new HashSet<>();
        void merge(TimeBucket o) {
            count += o.count; countFailed += o.countFailed; traffic += o.traffic;
            ips.addAll(o.ips); users.addAll(o.users); devices.addAll(o.devices);
            cells.addAll(o.cells); cities.addAll(o.cities);
        }
    }
}
