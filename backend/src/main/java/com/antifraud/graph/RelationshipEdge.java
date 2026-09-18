package com.antifraud.graph;

import java.util.*;

/** 带权重的有向边，支持时序标记 */
public class RelationshipEdge {
    private final String source, target;
    private final EdgeType type;
    private int weight;
    private long timestamp;
    private final Map<String, Object> attrs = new HashMap<>();

    public RelationshipEdge(String source, String target, EdgeType type, int weight, long timestamp) {
        this.source = source; this.target = target; this.type = type;
        this.weight = weight; this.timestamp = timestamp;
    }

    public enum EdgeType {
        TRANSFERRED_TO, SHARES_DEVICE, SHARES_IP, CONTACTED,
        VISITED_URL, URL_HOSTED_ON, BELONGS_TO
    }

    public String getSource() { return source; }
    public String getTarget() { return target; }
    public EdgeType getType() { return type; }
    public int getWeight() { return weight; }
    public void setWeight(int w) { this.weight = w; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long ts) { this.timestamp = ts; }
    public void setAttr(String k, Object v) { attrs.put(k, v); }
    public Object getAttr(String k) { return attrs.get(k); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof RelationshipEdge)) return false;
        RelationshipEdge e = (RelationshipEdge) o;
        return Objects.equals(source, e.source) && Objects.equals(target, e.target) && type == e.type;
    }
    @Override public int hashCode() { return Objects.hash(source, target, type); }
}
