package com.antifraud.ml;

import smile.classification.LogisticRegression;
import java.io.*;
import java.util.*;

/**
 * ML 训练器（独立运行，非服务）：
 * 生成网络侧 DPI 合成标注数据（欺诈率 ~3%）→ 训练 SMILE 逻辑回归 → 序列化 {model, means, stds}。
 * 运行：mvn exec:java -Dexec.mainClass="com.antifraud.ml.MlTrainer"（在 backend 目录）
 * 产物：src/main/resources/ml_model.ser（打包用）+ 根目录 ml_model.ser（本地运行用）
 */
public class MlTrainer {

    public static void main(String[] args) throws Exception {
        int n = 20000;
        double fraudRate = 0.03;
        Random rnd = new Random(42);

        double[][] x = new double[n][];
        int[] y = new int[n];
        int fraudCount = 0;
        for (int i = 0; i < n; i++) {
            boolean fraud = rnd.nextDouble() < fraudRate;
            x[i] = fraud ? fraudFeatures(rnd) : normalFeatures(rnd);
            x[i] = FeatureVector.logTransform(x[i]); // log1p 压缩，与推理端保持一致
            y[i] = fraud ? 1 : 0;
            if (fraud) fraudCount++;
        }

        LogisticRegression model = LogisticRegression.fit(x, y);

        int d = FeatureVector.ORDER.length;
        double[] means = new double[d];
        double[] stds = new double[d];
        for (int j = 0; j < d; j++) {
            double sum = 0;
            for (int i = 0; i < n; i++) sum += x[i][j];
            means[j] = sum / n;
            double sq = 0;
            for (int i = 0; i < n; i++) sq += (x[i][j] - means[j]) * (x[i][j] - means[j]);
            stds[j] = Math.sqrt(sq / n);
        }

        writeModel("src/main/resources/ml_model.ser", model, means, stds);
        writeModel("ml_model.ser", model, means, stds);
        System.out.println("[MlTrainer] 训练完成：样本=" + n + "，欺诈=" + fraudCount + "，特征维度=" + d);
    }

    /** 欺诈样本：访问涉诈网址/境外/诈骗电话短信/屏幕共享等特征偏高 */
    private static double[] fraudFeatures(Random rnd) {
        double[] f = new double[FeatureVector.ORDER.length];
        f[idx("user_login_count_1h")] = rnd.nextInt(3);
        f[idx("user_login_count_24h")] = rnd.nextInt(20);
        f[idx("user_failed_login_15m")] = rnd.nextDouble() < 0.4 ? rnd.nextInt(6) : 0;
        f[idx("user_fraud_url_count_1h")] = rnd.nextInt(6);
        f[idx("user_fraud_url_count_24h")] = rnd.nextInt(12);
        f[idx("user_suspicious_domain_count_24h")] = rnd.nextInt(10);
        f[idx("user_app_download_count_24h")] = rnd.nextInt(5);
        f[idx("user_fraud_call_count_24h")] = rnd.nextInt(8);
        f[idx("user_fraud_sms_count_24h")] = rnd.nextInt(8);
        f[idx("user_overseas_access_count_24h")] = rnd.nextInt(6);
        f[idx("abnormal_hour_access_count_24h")] = rnd.nextInt(5);
        f[idx("user_unique_ips_24h")] = 1 + rnd.nextInt(6);
        f[idx("user_unique_devices_24h")] = 1 + rnd.nextInt(5);
        f[idx("ip_event_count_1h")] = rnd.nextInt(15);
        f[idx("ip_unique_users_24h")] = rnd.nextInt(8);
        f[idx("device_unique_users_24h")] = rnd.nextInt(6);
        f[idx("user_screen_share_count_24h")] = rnd.nextDouble() < 0.6 ? 1 + rnd.nextInt(3) : 0;
        // 新增 10 维：欺诈用户被诱导转账 → 银行/会议 App 访问偏高，涉诈分类行为明显
        f[idx("user_bank_app_count_1h")] = rnd.nextInt(4);
        f[idx("user_bank_app_count_24h")] = rnd.nextInt(8);
        f[idx("user_meeting_app_count_24h")] = rnd.nextInt(5);
        f[idx("user_dating_app_count_24h")] = rnd.nextInt(6);
        f[idx("user_gambling_url_count_24h")] = rnd.nextInt(8);
        f[idx("user_shopping_url_count_24h")] = rnd.nextInt(5);
        f[idx("user_illegal_download_count_24h")] = rnd.nextInt(4);
        f[idx("user_traffic_bytes_24h")] = 100000 + rnd.nextInt(1000000);
        f[idx("user_unique_cells_24h")] = 1 + rnd.nextInt(5);
        f[idx("user_unique_cities_24h")] = 1 + rnd.nextInt(4);
        return f;
    }

    /** 正常样本：网络行为特征偏低 */
    private static double[] normalFeatures(Random rnd) {
        double[] f = new double[FeatureVector.ORDER.length];
        f[idx("user_login_count_1h")] = rnd.nextInt(4);
        f[idx("user_login_count_24h")] = rnd.nextInt(20);
        f[idx("user_failed_login_15m")] = rnd.nextDouble() < 0.1 ? 1 : 0;
        f[idx("user_fraud_url_count_1h")] = rnd.nextDouble() < 0.05 ? 1 : 0;
        f[idx("user_fraud_url_count_24h")] = rnd.nextDouble() < 0.1 ? 1 : 0;
        f[idx("user_suspicious_domain_count_24h")] = rnd.nextInt(2);
        f[idx("user_app_download_count_24h")] = rnd.nextInt(2);
        f[idx("user_fraud_call_count_24h")] = rnd.nextDouble() < 0.05 ? 1 : 0;
        f[idx("user_fraud_sms_count_24h")] = rnd.nextDouble() < 0.05 ? 1 : 0;
        f[idx("user_overseas_access_count_24h")] = rnd.nextInt(2);
        f[idx("abnormal_hour_access_count_24h")] = rnd.nextInt(2);
        f[idx("user_unique_ips_24h")] = 1 + rnd.nextInt(3);
        f[idx("user_unique_devices_24h")] = 1 + rnd.nextInt(2);
        f[idx("ip_event_count_1h")] = rnd.nextInt(8);
        f[idx("ip_unique_users_24h")] = rnd.nextInt(4);
        f[idx("device_unique_users_24h")] = rnd.nextInt(3);
        f[idx("user_screen_share_count_24h")] = 0;
        // 新增 10 维：正常用户偶尔访问银行/会议，涉诈分类行为基本为 0
        f[idx("user_bank_app_count_1h")] = rnd.nextInt(2);
        f[idx("user_bank_app_count_24h")] = rnd.nextInt(4);
        f[idx("user_meeting_app_count_24h")] = rnd.nextInt(2);
        f[idx("user_dating_app_count_24h")] = 0;
        f[idx("user_gambling_url_count_24h")] = 0;
        f[idx("user_shopping_url_count_24h")] = 0;
        f[idx("user_illegal_download_count_24h")] = 0;
        f[idx("user_traffic_bytes_24h")] = 10000 + rnd.nextInt(200000);
        f[idx("user_unique_cells_24h")] = 1 + rnd.nextInt(3);
        f[idx("user_unique_cities_24h")] = 1;
        return f;
    }

    private static int idx(String name) {
        for (int i = 0; i < FeatureVector.ORDER.length; i++) {
            if (FeatureVector.ORDER[i].equals(name)) return i;
        }
        return -1;
    }

    private static void writeModel(String path, LogisticRegression model, double[] means, double[] stds) throws Exception {
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            oos.writeObject(model);
            oos.writeObject(means);
            oos.writeObject(stds);
        }
        System.out.println("[MlTrainer] 已写入 " + f.getAbsolutePath());
    }
}
