package com.antifraud.simulator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DPI日志模拟器
 * 模拟银行网络深度包检测(DPI)日志
 * 包含正常行为 + 15%欺诈模式注入
 */
public class DpiLogSimulator {
    private static final String[] USERS = new String[200];
    private static final String[] FRAUD_USERS = {"user_0019","user_0033","user_0055","user_0072","user_0091"};
    private static final String[] CITIES = {"Beijing","Shanghai","Guangzhou","Shenzhen","Hangzhou","Chengdu"};
    private static final String[] EVENT_TYPES = {
        "login","logout","query_balance","transfer","modify_password",
        "bind_card","unbind_card","add_payee","reset_password","verify_code"
    };
    private static final String[] RISK_IPS = {
        "45.33.22.111","103.45.7.231","218.6.78.190","91.234.16.88","185.220.101.45"
    };
    private static final String[] FRAUD_RECEIVERS = {
        "fraud_acct_01","fraud_acct_02","fraud_acct_03","fraud_acct_04","fraud_acct_05"
    };
    private static final double FRAUD_RATIO = 0.15;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    static {
        for (int i = 0; i < 200; i++) USERS[i] = String.format("user_%04d", i + 1);
    }

    private final ThreadLocalRandom rnd = ThreadLocalRandom.current();

    /** 生成单条DPI日志 */
    public Map<String, Object> generate() {
        boolean isFraud = rnd.nextDouble() < FRAUD_RATIO;
        String user = isFraud ? FRAUD_USERS[rnd.nextInt(FRAUD_USERS.length)]
                              : USERS[rnd.nextInt(USERS.length)];
        String event = EVENT_TYPES[rnd.nextInt(EVENT_TYPES.length)];
        String ip = isFraud && rnd.nextDouble() < 0.6
                    ? RISK_IPS[rnd.nextInt(RISK_IPS.length)]
                    : rnd.nextInt(1,224) + "." + rnd.nextInt(256) + "." + rnd.nextInt(256) + "." + rnd.nextInt(1,255);

        Map<String, Object> log = new LinkedHashMap<>();
        log.put("log_id", UUID.randomUUID().toString());
        log.put("timestamp", LocalDateTime.now().format(FMT));
        log.put("session_id", UUID.randomUUID().toString().substring(0, 12));
        log.put("user_id", user);
        log.put("src_ip", ip);
        log.put("src_port", rnd.nextInt(1024, 65535));
        log.put("geo_city", CITIES[rnd.nextInt(CITIES.length)]);
        log.put("event_type", event);
        log.put("event_status", rnd.nextDouble() < 0.92 ? "success" : "failed");
        log.put("device_id", "dev_" + String.format("%04d", Math.abs((user + ip).hashCode()) % 10000));
        log.put("device_type", rnd.nextBoolean() ? "Android_14" : "iOS_17");
        log.put("connection", rnd.nextBoolean() ? "5G" : "WiFi");

        if ("transfer".equals(event)) {
            log.put("amount", isFraud ? new int[]{50000,100000,200000,500000}[rnd.nextInt(4)]
                                     : new int[]{100,500,1000,5000,10000,20000}[rnd.nextInt(6)]);
            log.put("receiver_id", isFraud ? FRAUD_RECEIVERS[rnd.nextInt(FRAUD_RECEIVERS.length)]
                                           : "user_" + String.format("%04d", rnd.nextInt(1, 200)));
            log.put("receiver_bank", new String[]{"ICBC","CCB","ABC","CMB","BOC"}[rnd.nextInt(5)]);
        }

        if (isFraud) {
            log.put("fraud_label", 1);
            if (rnd.nextDouble() < 0.3) log.put("ip_risk", "proxy/vpn");
            if (rnd.nextDouble() < 0.3) log.put("device_risk", "new_device");
        } else {
            log.put("fraud_label", 0);
        }

        return log;
    }

    /** 批量生成 */
    public List<Map<String, Object>> generateBatch(int count) {
        List<Map<String, Object>> batch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) batch.add(generate());
        return batch;
    }
}
