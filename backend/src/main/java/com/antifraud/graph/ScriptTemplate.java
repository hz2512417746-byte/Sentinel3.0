package com.antifraud.graph;

import java.util.List;

/** 诈骗剧本模板 */
public class ScriptTemplate {
    public final String name;
    public final double weight;
    public final List<String> steps;
    public final List<Long> maxIntervals; // 分钟

    public ScriptTemplate(String name, double weight, List<String> steps, List<Long> maxIntervals) {
        this.name = name; this.weight = weight; this.steps = steps; this.maxIntervals = maxIntervals;
    }
}
