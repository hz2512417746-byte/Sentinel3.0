package com.antifraud.config;

import com.antifraud.entity.RuleConfig;
import com.antifraud.repository.RuleConfigRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class RuleSeeder implements CommandLineRunner {
    private final RuleConfigRepository ruleRepo;

    public RuleSeeder(RuleConfigRepository ruleRepo) { this.ruleRepo = ruleRepo; }

    @Override public void run(String... args) {
        if (ruleRepo.count() > 0) return;

        // ── 1. 通用单特征规则（保留原有 DPI 行为规则） ──
        String[][] rules = {
            // 涉诈网址 / 域名
            {"短时高频访问涉诈网址","user_fraud_url_count_1h","3","1.5","1h访问涉诈网址≥3次"},
            {"日累计访问涉诈网址","user_fraud_url_count_24h","5","1.2","24h访问涉诈网址≥5次"},
            {"访问可疑域名","user_suspicious_domain_count_24h","5","1.0","24h访问可疑域名≥5次"},
            // APP 下载
            {"高频下载可疑APP","user_app_download_count_24h","3","1.2","24h下载可疑APP≥3次"},
            // 通话 / 短信
            {"诈骗电话频繁","user_fraud_call_count_24h","5","1.5","24h接通诈骗电话≥5次"},
            {"诈骗短信频繁","user_fraud_sms_count_24h","5","1.2","24h接收诈骗短信≥5次"},
            // 境外 / 异常时段
            {"境外异常访问","user_overseas_access_count_24h","3","1.5","24h境外访问≥3次"},
            {"凌晨异常访问","abnormal_hour_access_count_24h","3","1.2","凌晨0-5点访问≥3次"},
            // 屏幕共享
            {"屏幕共享异常","user_screen_share_count_24h","1","1.8","24h屏幕共享≥1次"},
            // 登录 / 设备
            {"高频登录失败","user_failed_login_15m","3","1.0","15min登录失败≥3次"},
            {"多新设备登录","user_unique_devices_24h","3","1.2","24h新设备≥3台"},
            // 账户 / 设备关联
            {"设备多账户","device_unique_users_24h","3","1.2","同设备24h关联≥3账户"},
            {"IP多账户","ip_unique_users_24h","5","1.2","同IP 24h关联≥5账户"},
            {"多账户同设备","device_unique_users_24h","5","1.8","多账户同设备/IP操作"},
            {"IP异常多用户","ip_unique_users_24h","8","1.5","同IP关联≥8用户"},
        };

        for (String[] r : rules) {
            RuleConfig rc = new RuleConfig();
            rc.setName(r[0]); rc.setFeature(r[1]);
            rc.setThreshold(Double.parseDouble(r[2]));
            rc.setWeight(Double.parseDouble(r[3]));
            rc.setDescription(r[4]); rc.setEnabled(true);
            ruleRepo.save(rc);
        }

        // ── 2. 湖州复杂规则（AND 组合 + 三级预警：低35min/中120min/高35min） ──
        // {规则名, 涉诈类型, 时间窗口, 风险等级, 权重, 描述, 条件(feature:阈值 用 ; 分隔)}
        String[][] complex = {
            // 冒充公检法
            {"冒充公检法-低风险","冒充公检法","35","1","1.2","35min涉诈网址≥1","user_fraud_url_count_35m:1"},
            {"冒充公检法-中风险","冒充公检法","120","2","1.4","120min涉诈网址≥2","user_fraud_url_count_120m:2"},
            {"冒充公检法-高风险","冒充公检法","35","3","2.0","35min涉诈网址≥1 且 银行App≥3","user_fraud_url_count_35m:1;user_bank_app_count_35m:3"},
            // 色情交友
            {"色情交友-低风险","色情交友","35","1","1.2","35min色情交友≥1","user_dating_app_count_35m:1"},
            {"色情交友-中风险","色情交友","120","2","1.4","120min色情交友≥5","user_dating_app_count_120m:5"},
            {"色情交友-高风险","色情交友","35","3","2.0","35min色情交友≥3 且 银行App≥3","user_dating_app_count_35m:3;user_bank_app_count_35m:3"},
            // 色情博彩
            {"色情博彩-低风险","色情博彩","35","1","1.2","35min色情博彩≥1","user_gambling_url_count_35m:1"},
            {"色情博彩-中风险","色情博彩","120","2","1.4","120min色情博彩≥6","user_gambling_url_count_120m:6"},
            {"色情博彩-高风险","色情博彩","35","3","2.0","35min色情博彩≥5 且 银行App≥1","user_gambling_url_count_35m:5;user_bank_app_count_35m:1"},
            // 虚假购物
            {"虚假购物-低风险","虚假购物","35","1","1.2","35min虚假购物≥1","user_shopping_url_count_35m:1"},
            {"虚假购物-中风险","虚假购物","120","2","1.4","120min虚假购物≥2","user_shopping_url_count_120m:2"},
            {"虚假购物-高风险","虚假购物","35","3","2.0","35min虚假购物≥2 且 银行App≥3","user_shopping_url_count_35m:2;user_bank_app_count_35m:3"},
            // 虚假贷款
            {"虚假贷款-低风险","虚假贷款","35","1","1.2","35min涉诈网址≥1","user_fraud_url_count_35m:1"},
            {"虚假贷款-中风险","虚假贷款","120","2","1.4","120min涉诈网址≥2","user_fraud_url_count_120m:2"},
            {"虚假贷款-高风险","虚假贷款","35","3","2.0","35min涉诈网址≥1 且 银行App≥3","user_fraud_url_count_35m:1;user_bank_app_count_35m:3"},
            // 投资理财
            {"投资理财-低风险","虚假投资理财","35","1","1.2","35min涉诈网址≥1","user_fraud_url_count_35m:1"},
            {"投资理财-中风险","虚假投资理财","120","2","1.4","120min涉诈网址≥3","user_fraud_url_count_120m:3"},
            {"投资理财-高风险","虚假投资理财","35","3","2.0","35min涉诈网址≥2 且 银行App≥1","user_fraud_url_count_35m:2;user_bank_app_count_35m:1"},
            // 刷单
            {"刷单诈骗-低风险","刷单诈骗","35","1","1.2","35min涉诈网址≥1","user_fraud_url_count_35m:1"},
            {"刷单诈骗-中风险","刷单诈骗","120","2","1.4","120min涉诈网址≥3","user_fraud_url_count_120m:3"},
            {"刷单诈骗-高风险","刷单诈骗","35","3","2.0","35min涉诈网址≥2 且 银行App≥1","user_fraud_url_count_35m:2;user_bank_app_count_35m:1"},
            // 游戏产品交易
            {"游戏交易-低风险","游戏产品交易","35","1","1.2","35min涉诈网址≥1","user_fraud_url_count_35m:1"},
            {"游戏交易-中风险","游戏产品交易","120","2","1.4","120min涉诈网址≥2","user_fraud_url_count_120m:2"},
            {"游戏交易-高风险","游戏产品交易","35","3","2.0","35min涉诈网址≥1 且 银行App≥1","user_fraud_url_count_35m:1;user_bank_app_count_35m:1"},
        };

        for (String[] r : complex) {
            RuleConfig rc = new RuleConfig();
            rc.setName(r[0]); rc.setFraudType(r[1]);
            rc.setTimeWindow(Integer.parseInt(r[2]));
            rc.setRiskLevel(Integer.parseInt(r[3]));
            rc.setWeight(Double.parseDouble(r[4]));
            rc.setDescription(r[5]);
            rc.setConditionJson(buildConditions(r[6]));
            rc.setFeature(""); rc.setThreshold(0.0);  // 复杂规则不使用单特征字段
            rc.setEnabled(true);
            ruleRepo.save(rc);
        }
        System.out.println("[RuleSeeder] 已初始化 " + rules.length + " 条通用规则 + " + complex.length + " 条湖州复杂规则");
    }

    /** 将 "feature:阈值;feature:阈值" 转为 conditionJson */
    private String buildConditions(String conds) {
        String[] parts = conds.split(";");
        StringBuilder sb = new StringBuilder("{\"conditions\":[");
        for (int i = 0; i < parts.length; i++) {
            String[] kv = parts[i].split(":");
            if (i > 0) sb.append(",");
            sb.append("{\"feature\":\"").append(kv[0]).append("\",\"threshold\":").append(kv[1]).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
}
