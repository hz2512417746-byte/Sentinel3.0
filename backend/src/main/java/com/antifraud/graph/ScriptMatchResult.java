package com.antifraud.graph;

public class ScriptMatchResult {
    public static final ScriptMatchResult NO_MATCH = new ScriptMatchResult(null, 0, "NONE", 0);
    public final String templateName;
    public final double score;
    public final String level;
    public final double weight;

    public ScriptMatchResult(String name, double score, String level, double weight) {
        this.templateName = name; this.score = score; this.level = level; this.weight = weight;
    }
    public boolean isMatch() { return score >= 0.55 && templateName != null; }
}
