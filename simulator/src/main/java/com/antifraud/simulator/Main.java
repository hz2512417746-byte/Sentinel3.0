package com.antifraud.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** 网络侧 DPI 日志模拟器 + Kafka 生产者 */
public class Main {
    private static final String[] USERS = new String[200];
    static { for (int i=0; i<200; i++) USERS[i]=String.format("user_%04d",i+1); }
    private static final List<String> FRAUD_LIST = new ArrayList<>();
    private static final Set<String> FRAUD_SET = new HashSet<>();
    static {
        List<String> all = new ArrayList<>(Arrays.asList(USERS));
        Collections.shuffle(all, new Random(42));
        for(int i=0;i<10;i++) { String u=all.get(i); FRAUD_LIST.add(u); FRAUD_SET.add(u); }
    }
    private static final String[] CITIES = {"Beijing","Shanghai","Guangzhou","Shenzhen","Hangzhou","Chengdu"};
    // 每个用户的稳定画像：IP/设备/小区/城市（正常用户偶尔才变化，避免 unique_ips/cells/devices 爆表）
    private static final Map<String, String[]> PROFILES = new HashMap<>();
    static {
        Random r = new Random(7);
        for (String u : USERS) {
            String ip = r.nextInt(1,224)+"."+r.nextInt(256)+"."+r.nextInt(256)+"."+r.nextInt(1,255);
            String dev = "dev_"+String.format("%06d", r.nextInt(1000000));
            String cell = String.valueOf(r.nextLong(1000000, 9999999));
            String city = CITIES[r.nextInt(CITIES.length)];
            PROFILES.put(u, new String[]{ip, dev, cell, city});
        }
    }
    // 欺诈用户偏向的涉诈行为事件（含银行 App 访问——被诱导转账前的关键行为）
    private static final String[] FRAUD_EVENTS = {"visit_fraud_url","download_app","fraud_call","fraud_sms","abnormal_overseas","screen_share","visit_suspicious_domain","login","visit_bank_app","visit_meeting_app"};
    // 正常用户：绝大多数是中性行为(浏览/视频/聊天/社交)，登录/银行/改密等特征事件低频——避免 24h 累计特征爆表
    private static String normalEvent(ThreadLocalRandom rnd) {
        double r = rnd.nextDouble();
        if (r < 0.55) return "visit_web";
        if (r < 0.72) return "video_play";
        if (r < 0.87) return "chat_message";
        if (r < 0.96) return "social_browse";
        if (r < 0.98) return "login";            // 2% 登录
        if (r < 0.985) return "visit_bank_app";  // 0.5% 银行
        if (r < 0.993) return "modify_password"; // 0.8% 改密
        return "new_device_login";               // 0.7% 新设备登录
    }
    private static final String[] RISK_IPS = {"45.33.22.111","103.45.7.231","218.6.78.190","91.234.16.88","185.220.101.45"};
    private static final String[] FRAUD_DOMAINS = {"a1.cc","a2.cn","a3.cc","xbet.cc","loanz.cc","kefu365.com"};
    private static final String[] NORMAL_DOMAINS = {"taobao.com","jd.com","baidu.com","qq.com","weibo.com"};
    private static final String[] SUSPICIOUS_APPS = {"XX贷款","XX投资","XX刷单","XX交友","XX博彩"};
    private static final String[] BANK_APPS = {"XX银行","XX支付","XX金融","XX证券","XX钱包"};
    private static final String[] MEETING_APPS = {"钉钉会议","腾讯会议","飞书会议","企业微信","Zoom"};
    // 涉诈网址分类（对齐 FraudType 枚举 16 类）
    private static final String[] FRAUD_URL_CATEGORIES = {"冒充公检法","冒充客服退款","冒充熟人","虚假贷款","刷单诈骗","虚假投资理财","杀猪盘","色情交友","色情博彩","虚假购物","游戏产品交易","钓鱼盗号","裸聊敲诈","防封系统","黑灰产","非法分发"};
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrap);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("batch.size", "16384");
        props.put("linger.ms", "5");

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        System.out.println("[Simulator] Kafka connected: " + bootstrap);

        int count = 0, rate = 80;
        System.out.println("[Simulator] Rate: " + rate + "/s, Fraud: 10%");

        while (true) {
            int batch = rnd.nextInt(1, 5);
            for (int i = 0; i < batch; i++) {
                boolean fraud = rnd.nextDouble() < 0.10;
                String user = fraud ? FRAUD_LIST.get(rnd.nextInt(FRAUD_LIST.size())) : USERS[rnd.nextInt(USERS.length)];
                String event = fraud ? FRAUD_EVENTS[rnd.nextInt(FRAUD_EVENTS.length)] : normalEvent(rnd);
                String[] prof = PROFILES.get(user);
                String ip = prof[0];
                if (fraud && rnd.nextDouble() < 0.6) ip = RISK_IPS[rnd.nextInt(RISK_IPS.length)];
                else if (rnd.nextDouble() < 0.02) ip = rnd.nextInt(1,224)+"."+rnd.nextInt(256)+"."+rnd.nextInt(256)+"."+rnd.nextInt(1,255);

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("log_id", UUID.randomUUID().toString());
                entry.put("timestamp", LocalDateTime.now().format(FMT));
                entry.put("session_id", UUID.randomUUID().toString().substring(0,12));
                entry.put("user_id", user);
                entry.put("src_ip", ip);
                entry.put("src_port", rnd.nextInt(1024, 65535));
                entry.put("geo_city", prof[3]);
                entry.put("event_type", event);
                entry.put("event_status", rnd.nextDouble() < 0.92 ? "success" : "failed");
                entry.put("device_id", "new_device_login".equals(event) && rnd.nextDouble() < 0.5 ? "dev_"+String.format("%06d", rnd.nextInt(1000000)) : prof[1]);
                entry.put("device_type", new String[]{"Android_14","iOS_17","Windows_Chrome"}[rnd.nextInt(3)]);
                entry.put("connection", new String[]{"5G","WiFi","4G"}[rnd.nextInt(3)]);
                entry.put("fraud_label", fraud ? 1 : 0);

                // 运营商信令字段（HTTP/HTTPS 信令）
                entry.put("msisdn", "139" + rnd.nextInt(10000000, 99999999));
                entry.put("tac", rnd.nextInt(10000, 30000));
                entry.put("cell_id", Long.parseLong(prof[2]));
                entry.put("lac_ci", rnd.nextInt(1000, 9999) + "-" + rnd.nextInt(100000, 999999));
                entry.put("app_type", rnd.nextInt(1, 40));
                entry.put("app_sub_type", rnd.nextLong(100, 9999));
                entry.put("app_content", rnd.nextInt(0, 100));
                entry.put("app_status", rnd.nextInt(0, 8));
                entry.put("dl_data", rnd.nextLong(1000, 5000000));
                entry.put("server_ip", rnd.nextInt(1, 224) + "." + rnd.nextInt(256) + "." + rnd.nextInt(256) + "." + rnd.nextInt(1, 255));
                entry.put("server_port", rnd.nextBoolean() ? 443 : 80);
                entry.put("sni", (rnd.nextBoolean() ? "www." : "") + NORMAL_DOMAINS[rnd.nextInt(NORMAL_DOMAINS.length)]);
                entry.put("refer_uri", "https://" + NORMAL_DOMAINS[rnd.nextInt(NORMAL_DOMAINS.length)] + "/ref/" + rnd.nextInt(100, 999));

                // DPI 通联字段：域名/URL
                if (rnd.nextDouble() < 0.5) {
                    String domain = fraud ? FRAUD_DOMAINS[rnd.nextInt(FRAUD_DOMAINS.length)] : NORMAL_DOMAINS[rnd.nextInt(NORMAL_DOMAINS.length)];
                    entry.put("domain", domain);
                    entry.put("url", "https://" + domain + "/" + rnd.nextInt(1000, 9999));
                    entry.put("protocol", rnd.nextBoolean() ? "HTTPS" : "HTTP");
                    entry.put("uri_depth", fraud ? rnd.nextInt(3, 6) : rnd.nextInt(1, 3));
                }
                // 下载 APP 事件附带 APP 名 + 非法分发分类
                if ("download_app".equals(event)) {
                    entry.put("app_name", SUSPICIOUS_APPS[rnd.nextInt(SUSPICIOUS_APPS.length)]);
                    entry.put("fraud_url_category", "非法分发");
                }
                // 银行/会议 App 访问事件附带 APP 名
                if ("visit_bank_app".equals(event)) {
                    entry.put("app_name", BANK_APPS[rnd.nextInt(BANK_APPS.length)]);
                }
                if ("visit_meeting_app".equals(event)) {
                    entry.put("app_name", MEETING_APPS[rnd.nextInt(MEETING_APPS.length)]);
                }
                // 涉诈网址访问附带湖州分类
                if ("visit_fraud_url".equals(event)) {
                    entry.put("fraud_url_category", FRAUD_URL_CATEGORIES[rnd.nextInt(FRAUD_URL_CATEGORIES.length)]);
                }
                // 诈骗电话/短信事件附带通联号码与时长
                if ("fraud_call".equals(event) || "fraud_sms".equals(event)) {
                    entry.put("contact_phone", "138" + rnd.nextInt(10000000, 99999999));
                    entry.put("contact_type", "fraud_call".equals(event) ? "call_out" : "sms_in");
                    entry.put("contact_duration", rnd.nextInt(30, 600));
                }
                if (fraud && rnd.nextDouble() < 0.5) entry.put("ip_risk", "proxy/vpn");

                String json = JSON.writeValueAsString(entry);
                producer.send(new ProducerRecord<>("dpi_logs", user, json), (meta, ex) -> { if (ex != null) System.err.println("[Sim] Send failed: " + ex.getMessage()); });
                count++;
            }
            if (count % 100 == 0) System.out.println("[Simulator] " + count + " logs generated");
            Thread.sleep(rnd.nextLong(80, 170));
        }
    }
}
