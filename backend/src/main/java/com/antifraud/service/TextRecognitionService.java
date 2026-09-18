package com.antifraud.service;

import com.antifraud.config.LogWebSocketHandler;
import com.antifraud.entity.FraudType;
import com.antifraud.entity.LogEvent;
import com.antifraud.repository.LogEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

/** 文本识别编排：LLM 优先 → 关键词兜底 → 落一条 text_detect 日志 + WebSocket 推送 */
@Service
public class TextRecognitionService {

    private final LlmService llm;
    private final TextFraudClassifier keyword;
    private final LogEventRepository logRepo;
    private final LogWebSocketHandler logHandler;
    private final ObjectMapper json = new ObjectMapper();

    public TextRecognitionService(LlmService llm, TextFraudClassifier keyword,
                                  LogEventRepository logRepo, LogWebSocketHandler logHandler) {
        this.llm = llm; this.keyword = keyword; this.logRepo = logRepo; this.logHandler = logHandler;
    }

    public Map<String, Object> recognize(String text) {
        if (text == null || text.isBlank()) {
            return Map.of("error", "文本不能为空");
        }
        String fraudType = "正常";
        double confidence = 0;
        String reason = "";
        boolean usedLlm = false;

        // 1. LLM 优先
        String llmOut = llm.generate(buildPrompt(text));
        if (llmOut != null) {
            Map<String, Object> parsed = parseJson(llmOut);
            if (parsed != null && parsed.get("fraudType") != null) {
                fraudType = String.valueOf(parsed.get("fraudType"));
                confidence = toDouble(parsed.get("confidence"));
                reason = String.valueOf(parsed.getOrDefault("reason", ""));
                usedLlm = true;
            }
        }
        // 2. LLM 失败/离线 → 关键词兜底
        if (!usedLlm) {
            Map<String, Object> kw = keyword.classify(text);
            if (!kw.isEmpty()) {
                fraudType = String.valueOf(kw.get("fraudType"));
                confidence = toDouble(kw.get("confidence"));
                reason = String.valueOf(kw.get("reason")) + "（关键词兜底）";
            } else {
                reason = "未命中已知诈骗特征，建议人工复核";
            }
        }

        boolean isFraud = !"正常".equals(fraudType) && confidence > 0;
        int riskLevel = isFraud ? (confidence >= 0.7 ? 3 : 2) : 0;
        String decision = riskLevel >= 3 ? "block" : riskLevel >= 2 ? "warn" : "allow";

        // 3. 落日志（eventType=text_detect）
        LogEvent log = new LogEvent();
        log.setLogId(UUID.randomUUID().toString());
        log.setSrcIp("-");
        log.setEventType("text_detect");
        log.setTextContent(text);
        log.setInputType("text");
        log.setFraudType(fraudType);
        log.setFraudConfidence(confidence);
        log.setRiskLevel(riskLevel);
        log.setRiskScore(confidence);
        log.setDecision(decision);
        log.setIsBlocked(riskLevel >= 3);
        log.setHitRules(reason);
        log.setTimestamp(LocalDateTime.now());
        logRepo.save(log);

        // 4. WebSocket 推送（前端实时日志流可见）
        try {
            Map<String, Object> wsMsg = new LinkedHashMap<>();
            wsMsg.put("type", "log");
            wsMsg.put("data", log);
            logHandler.broadcast(json.writeValueAsString(wsMsg));
        } catch (Exception ignored) {}

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logId", log.getLogId());
        result.put("fraudType", fraudType);
        result.put("confidence", confidence);
        result.put("reason", reason);
        result.put("riskLevel", riskLevel);
        result.put("decision", decision);
        result.put("usedLlm", usedLlm);
        return result;
    }

    private String buildPrompt(String text) {
        return "你是反诈文本识别助手。请判断以下文本是否属于诈骗，并识别诈骗类型。\n" +
            "诈骗类型可选：" + String.join("、", FraudType.names()) + "。\n" +
            "如果不是诈骗，fraudType 填 \"正常\"。\n" +
            "只输出一个 JSON 对象，不要输出任何其他文字，格式：{\"fraudType\":\"类型\",\"confidence\":0到1之间的小数,\"reason\":\"简短理由\"}\n\n" +
            "文本内容：\n" + text;
    }

    /** 从 LLM 输出中宽松提取首个 JSON 对象 */
    private Map<String, Object> parseJson(String s) {
        if (s == null) return null;
        try {
            Matcher m = Pattern.compile("\\{[\\s\\S]*\\}").matcher(s);
            if (m.find()) return json.readValue(m.group(), Map.class);
        } catch (Exception ignored) {}
        return null;
    }

    private static double toDouble(Object v) {
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }
}
