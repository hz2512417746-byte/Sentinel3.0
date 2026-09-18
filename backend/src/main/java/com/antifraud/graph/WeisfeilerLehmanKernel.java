package com.antifraud.graph;

import org.jgrapht.Graph;
import java.util.*;

/**
 * Weisfeiler-Lehman 图核：计算两个图的结构相似度
 * 迭代3轮，每轮收集邻居标签→排序→哈希→图指纹向量→余弦相似度
 * 1000节点子图 <50ms
 */
public class WeisfeilerLehmanKernel {
    private final int iterations;
    public WeisfeilerLehmanKernel() { this(3); }
    public WeisfeilerLehmanKernel(int h) { this.iterations = h; }

    public double similarity(Graph<String, RelationshipEdge> g1, Graph<String, RelationshipEdge> g2) {
        Map<String, Integer> fp1 = fingerprint(g1), fp2 = fingerprint(g2);
        return cosine(fp1, fp2);
    }

    public Map<String, Integer> fingerprint(Graph<String, RelationshipEdge> g) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (String v : g.vertexSet()) labels.put(v, initLabel(v, g));
        for (String l : labels.values()) counts.merge("h0_" + l, 1, Integer::sum);
        for (int iter = 1; iter <= iterations; iter++) {
            Map<String, String> next = new HashMap<>();
            for (String v : g.vertexSet()) {
                List<String> neighbors = new ArrayList<>();
                for (RelationshipEdge e : g.edgesOf(v))
                    neighbors.add(labels.get(e.getSource().equals(v) ? e.getTarget() : e.getSource()));
                Collections.sort(neighbors);
                String combined = labels.get(v) + "|" + String.join(",", neighbors) + "|h" + iter;
                next.put(v, Integer.toHexString(combined.hashCode()));
            }
            labels = next;
            for (String l : labels.values()) counts.merge("h" + iter + "_" + l, 1, Integer::sum);
        }
        return counts;
    }

    private String initLabel(String v, Graph<String, RelationshipEdge> g) {
        int d = g.degreeOf(v);
        String prefix = v.startsWith("User:") ? "U" : v.startsWith("Device:") ? "D" :
                        v.startsWith("IP:") ? "I" : v.startsWith("Account:") ? "A" : "N";
        return prefix + (d <= 1 ? "0" : d <= 3 ? "1" : d <= 10 ? "2" : d <= 50 ? "3" : "4");
    }

    private double cosine(Map<String, Integer> a, Map<String, Integer> b) {
        Set<String> all = new HashSet<>(); all.addAll(a.keySet()); all.addAll(b.keySet());
        double dot = 0, na = 0, nb = 0;
        for (String k : all) {
            double va = a.getOrDefault(k, 0), vb = b.getOrDefault(k, 0);
            dot += va * vb; na += va * va; nb += vb * vb;
        }
        return (na < 1e-9 || nb < 1e-9) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
