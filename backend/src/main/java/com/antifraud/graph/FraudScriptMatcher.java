package com.antifraud.graph;

import org.springframework.stereotype.Service;
import java.util.*;

/** 诈骗剧本弹性LCS匹配——允许跳过缺失步骤 */
@Service
public class FraudScriptMatcher {

    private final GraphEngine graphEngine;

    private static final List<ScriptTemplate> TEMPLATES = List.of(
        new ScriptTemplate("杀猪盘", 2.0, List.of("TRANSFER_SMALL", "CONTACT", "TRANSFER_LARGE"), List.of(1440L, 120L, 60L)),
        new ScriptTemplate("冒充公检法", 2.5, List.of("CONTACT", "LOGIN", "CONTACT", "TRANSFER_LARGE"), List.of(60L, 30L, 30L, 120L)),
        new ScriptTemplate("刷单诈骗", 1.8, List.of("TRANSFER_SMALL", "VERIFY_CODE", "TRANSFER_LARGE"), List.of(60L, 30L, 30L)),
        new ScriptTemplate("钓鱼盗号", 2.2, List.of("LOGIN", "MODIFY_PASSWORD", "ADD_PAYEE", "TRANSFER_MEDIUM"), List.of(120L, 30L, 30L, 30L))
    );

    public FraudScriptMatcher(GraphEngine graphEngine) { this.graphEngine = graphEngine; }

    public ScriptMatchResult match(String userId, int windowMinutes) {
        List<TimedEvent> timeline = graphEngine.getUserTimeline(userId);
        if (timeline == null || timeline.size() < 2) return ScriptMatchResult.NO_MATCH;
        long cutoff = System.currentTimeMillis() - windowMinutes * 60000L;
        List<String> types = timeline.stream().filter(e -> e.timestamp >= cutoff).map(TimedEvent::normalizedType).filter(t -> !"UNKNOWN".equals(t)).toList();
        if (types.size() < 2) return ScriptMatchResult.NO_MATCH;

        ScriptMatchResult best = ScriptMatchResult.NO_MATCH;
        List<TimedEvent> filtered = timeline.stream().filter(e -> e.timestamp >= cutoff).toList();
        for (ScriptTemplate tpl : TEMPLATES) {
            double score = elasticLcs(types, filtered, tpl);
            if (score > best.score) best = new ScriptMatchResult(tpl.name, score, score >= 0.75 ? "HIGH" : score >= 0.55 ? "MEDIUM" : "LOW", tpl.weight);
        }
        return best;
    }

    private double elasticLcs(List<String> types, List<TimedEvent> events, ScriptTemplate tpl) {
        int n = types.size(), m = tpl.steps.size();
        double[][] dp = new double[n + 1][m + 1];
        for (int i = 1; i <= n; i++) dp[i][0] = dp[i - 1][0] - 0.05;
        for (int j = 1; j <= m; j++) dp[0][j] = dp[0][j - 1] - 0.15;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                double typeScore = types.get(i - 1).equals(tpl.steps.get(j - 1)) ? 1.0 :
                    tpl.steps.get(j - 1).startsWith("TRANSFER") && types.get(i - 1).startsWith("TRANSFER") ? 0.5 : 0;
                double timeDecay = 1.0;
                if (j > 1 && typeScore > 0.5) {
                    long prevTime = 0;
                    for (int k = i - 1; k >= 0; k--) {
                        if (typeMatch(types.get(k), tpl.steps.get(j - 2))) { prevTime = events.get(k).timestamp; break; }
                    }
                    if (prevTime > 0) {
                        long gap = events.get(i - 1).timestamp - prevTime;
                        long maxGap = tpl.maxIntervals.get(j - 1) * 60000L;
                        if (gap > maxGap) timeDecay = Math.exp(-(double)(gap - maxGap) / 3600000.0);
                    }
                }
                dp[i][j] = Math.max(Math.max(dp[i - 1][j] - 0.10, dp[i][j - 1] - 0.15), dp[i - 1][j - 1] + typeScore * timeDecay);
            }
        }
        return Math.max(0, dp[n][m] / m);
    }

    private boolean typeMatch(String a, String b) { return a.equals(b) || (b.startsWith("TRANSFER") && a.startsWith("TRANSFER")); }

    public static List<ScriptTemplate> getTemplates() { return Collections.unmodifiableList(TEMPLATES); }
}
