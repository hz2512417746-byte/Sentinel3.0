package com.antifraud.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/** LLM 客户端：调用本地 Ollama 大模型（预留配置 llm.*），识别诈骗文本 */
@Service
public class LlmService {

    @Value("${llm.enabled:false}")
    private boolean enabled;

    @Value("${llm.endpoint:http://localhost:11434/api/generate}")
    private String endpoint;

    @Value("${llm.model:qwen2.5:7b}")
    private String model;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3)).build();

    public boolean isEnabled() { return enabled; }

    /** 调用 LLM 生成文本（带超时，失败/未启用返回 null） */
    public String generate(String prompt) {
        if (!enabled) return null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("prompt", prompt);
            body.put("stream", false);
            Map<String, Object> opts = new LinkedHashMap<>();
            opts.put("temperature", 0.1);
            body.put("options", opts);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            Map<String, Object> result = json.readValue(resp.body(), Map.class);
            Object response = result.get("response");
            return response != null ? response.toString() : null;
        } catch (Exception e) {
            System.err.println("[LLM] 调用失败: " + e.getMessage());
            return null;
        }
    }
}
