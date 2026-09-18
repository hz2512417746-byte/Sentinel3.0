package com.antifraud.service;

import com.antifraud.entity.FraudType;
import org.springframework.stereotype.Service;
import java.util.*;

/** 文本诈骗分类器（关键词兜底）：LLM 不可用时用关键词库识别诈骗类型（16 类统一枚举） */
@Service
public class TextFraudClassifier {

    // 反诈类型 → 关键词列表（命中越多置信度越高），由 FraudType 枚举统一提供
    private static final LinkedHashMap<String, List<String>> KEYWORDS = new LinkedHashMap<>();
    static {
        for (FraudType t : FraudType.values()) {
            KEYWORDS.put(t.getDisplayName(), t.getKeywords());
        }
    }

    /** 返回 {fraudType, confidence, reason}；无命中返回空 Map */
    public Map<String, Object> classify(String text) {
        if (text == null || text.isBlank()) return Map.of();
        String best = null; int bestHits = 0; List<String> bestWords = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : KEYWORDS.entrySet()) {
            int hits = 0; List<String> matched = new ArrayList<>();
            for (String kw : e.getValue()) {
                if (text.contains(kw)) { hits++; matched.add(kw); }
            }
            if (hits > bestHits) { bestHits = hits; best = e.getKey(); bestWords = matched; }
        }
        if (best == null) return Map.of();
        double confidence = Math.min(0.95, 0.4 + 0.15 * bestHits);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fraudType", best);
        r.put("confidence", confidence);
        r.put("reason", "命中关键词: " + String.join("、", bestWords));
        return r;
    }
}
