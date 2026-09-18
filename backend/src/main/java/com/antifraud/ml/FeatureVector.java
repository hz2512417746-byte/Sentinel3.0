package com.antifraud.ml;

import java.util.*;

/** 特征向量工具：固定27维网络侧 DPI 特征顺序，保证训练和推理一致 */
public class FeatureVector {

    /** 27 维网络侧 DPI 特征固定顺序，与 ZengKuaiEngine 输出对齐 */
    public static final String[] ORDER = {
        "user_login_count_1h",
        "user_login_count_24h",
        "user_failed_login_15m",
        "user_fraud_url_count_1h",
        "user_fraud_url_count_24h",
        "user_suspicious_domain_count_24h",
        "user_app_download_count_24h",
        "user_fraud_call_count_24h",
        "user_fraud_sms_count_24h",
        "user_overseas_access_count_24h",
        "abnormal_hour_access_count_24h",
        "user_unique_ips_24h",
        "user_unique_devices_24h",
        "ip_event_count_1h",
        "ip_unique_users_24h",
        "device_unique_users_24h",
        "user_screen_share_count_24h",
        "user_bank_app_count_1h",
        "user_bank_app_count_24h",
        "user_meeting_app_count_24h",
        "user_dating_app_count_24h",
        "user_gambling_url_count_24h",
        "user_shopping_url_count_24h",
        "user_illegal_download_count_24h",
        "user_traffic_bytes_24h",
        "user_unique_cells_24h",
        "user_unique_cities_24h"
    };

    /** 从 features Map 提取固定顺序的 double[] */
    public static double[] extract(Map<String, Double> features) {
        double[] x = new double[ORDER.length];
        for (int i = 0; i < ORDER.length; i++) {
            Double v = features.get(ORDER[i]);
            x[i] = v != null ? v : 0.0;
        }
        return x;
    }

    /** 对计数特征统一做 log1p 压缩，压制重尾长尾（训练与推理必须一致，避免线上 24h 累计特征量级爆表） */
    public static double[] logTransform(double[] x) {
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = Math.log1p(Math.max(0.0, x[i]));
        }
        return y;
    }
}
