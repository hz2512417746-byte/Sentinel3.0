package com.antifraud.service;

import com.antifraud.ml.FeatureVector;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import smile.classification.LogisticRegression;
import java.io.*;
import java.util.*;

/** ML 评分服务：加载 SMILE 逻辑回归模型，提供 predict() 接口（返回欺诈后验概率 [0,1]） */
@Service
public class MlScoringService {

    private LogisticRegression model;
    private double[] means, stds;
    private boolean modelLoaded = false;

    @PostConstruct
    public void init() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("ml_model.ser")) {
            if (is == null) {
                System.out.println("[ML] 模型文件 ml_model.ser 不存在，使用手写加权作为回退");
                return;
            }
            try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(is))) {
                this.model = (LogisticRegression) ois.readObject();
                this.means = (double[]) ois.readObject();
                this.stds = (double[]) ois.readObject();
                // 特征维度校验：模型与当前 FeatureVector.ORDER 必须一致，否则回退
                if (means == null || stds == null
                        || means.length != FeatureVector.ORDER.length
                        || stds.length != FeatureVector.ORDER.length) {
                    System.out.println("[ML] 模型特征维度不匹配（模型=" + (means != null ? means.length : 0)
                            + "，当前=" + FeatureVector.ORDER.length + "），使用手写加权回退");
                    return;
                }
                this.modelLoaded = true;
                System.out.println("[ML] SMILE 逻辑回归模型加载成功 (特征维度=" + FeatureVector.ORDER.length + ")");
            }
        } catch (Exception e) {
            System.out.println("[ML] 模型加载失败: " + e.getMessage() + "，使用手写加权回退");
        }
    }

    /** 预测欺诈概率 [0, 1]（连续值，取欺诈类后验概率） */
    public double predict(Map<String, Double> features) {
        if (!modelLoaded || model == null) {
            return fallbackScore(features);
        }
        double[] x = FeatureVector.extract(features);
        x = FeatureVector.logTransform(x); // log1p 压缩，与训练端保持一致
        // 标准化
        for (int i = 0; i < x.length; i++) {
            x[i] = stds[i] > 1e-8 ? (x[i] - means[i]) / stds[i] : 0;
        }
        // 二分类：posteriori[0]=正常概率，posteriori[1]=欺诈概率
        double[] posteriori = new double[2];
        model.predict(x, posteriori);
        return posteriori.length > 1 ? posteriori[1] : posteriori[0];
    }

    public boolean isModelLoaded() { return modelLoaded; }

    // ─── 手写加权回退（模型缺失时，基于网络侧 DPI 特征） ───
    private double fallbackScore(Map<String, Double> f) {
        double s = 0, w = 0;
        double fu = f.getOrDefault("user_fraud_url_count_1h", 0.0);
        if (fu >= 3) { s += 0.30; w += 0.20; } else if (fu >= 1) { s += 0.15; w += 0.15; }
        double ov = f.getOrDefault("user_overseas_access_count_24h", 0.0);
        if (ov >= 3) { s += 0.25; w += 0.20; } else if (ov >= 1) { s += 0.10; w += 0.15; }
        double ss = f.getOrDefault("user_screen_share_count_24h", 0.0);
        if (ss >= 1) { s += 0.30; w += 0.20; }
        double fc = f.getOrDefault("user_fraud_call_count_24h", 0.0);
        if (fc >= 5) { s += 0.25; w += 0.20; } else if (fc >= 1) { s += 0.10; w += 0.15; }
        double iu = f.getOrDefault("ip_unique_users_24h", 0.0);
        if (iu >= 5) { s += 0.25; w += 0.20; } else if (iu >= 2) { s += 0.15; w += 0.15; }
        double du = f.getOrDefault("device_unique_users_24h", 0.0);
        if (du >= 5) { s += 0.25; w += 0.20; } else if (du >= 2) { s += 0.15; w += 0.15; }
        return w > 0 ? Math.min(1.0, s / w) : 0;
    }
}
