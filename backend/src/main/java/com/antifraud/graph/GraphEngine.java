package com.antifraud.graph;

import org.jgrapht.Graph;
import org.jgrapht.graph.Pseudograph;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 轻量级内存图引擎——维护账户-设备-IP关系图，用于团伙识别和社区发现 */
@Service
public class GraphEngine {

    private final Graph<String, RelationshipEdge> relationGraph = new Pseudograph<>(RelationshipEdge.class);
    private final Map<String, List<TimedEvent>> userTimelines = new ConcurrentHashMap<>();
    private static final int MAX_TIMELINE = 200;

    /** 处理一条事件，更新关系图 */
    public void ingest(Map<String, Object> event) {
        long now = System.currentTimeMillis();
        String userId = (String) event.getOrDefault("user_id", "");
        String ip = (String) event.getOrDefault("src_ip", "");
        String device = (String) event.getOrDefault("device_id", "");
        String receiver = (String) event.getOrDefault("receiver_id", "");
        String eventType = (String) event.getOrDefault("event_type", "");
        double amount = toDouble(event.get("amount"));

        if (userId.isEmpty()) return;
        String userNode = "User:" + userId;

        // 用户-设备边
        if (!device.isEmpty()) {
            String devNode = "Device:" + device;
            addOrUpdateEdge(relationGraph, userNode, devNode, RelationshipEdge.EdgeType.SHARES_DEVICE, now);
        }
        // 用户-IP边
        if (!ip.isEmpty()) {
            String ipNode = "IP:" + ip;
            addOrUpdateEdge(relationGraph, userNode, ipNode, RelationshipEdge.EdgeType.SHARES_IP, now);
        }
        // 用户-收款方边
        if (!receiver.isEmpty()) {
            String recvNode = "Account:" + receiver;
            addOrUpdateEdge(relationGraph, userNode, recvNode, RelationshipEdge.EdgeType.TRANSFERRED_TO, now);
        }

        // 用户时间线
        userTimelines.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new TimedEvent(now, eventType, receiver, amount, userId));
        List<TimedEvent> tl = userTimelines.get(userId);
        if (tl != null && tl.size() > MAX_TIMELINE) tl.subList(0, tl.size() - MAX_TIMELINE).clear();
    }

    private void addOrUpdateEdge(Graph<String, RelationshipEdge> g, String src, String tgt,
                                  RelationshipEdge.EdgeType type, long ts) {
        g.addVertex(src); g.addVertex(tgt);
        RelationshipEdge existing = null;
        for (RelationshipEdge e : g.getAllEdges(src, tgt)) {
            if (e.getType() == type) { existing = e; break; }
        }
        if (existing != null) {
            Integer cnt = (Integer) existing.getAttr("count");
            existing.setAttr("count", (cnt != null ? cnt : 0) + 1);
            existing.setTimestamp(ts);
        } else {
            RelationshipEdge e = new RelationshipEdge(src, tgt, type, 1, ts);
            e.setAttr("count", 1);
            g.addEdge(src, tgt, e);
        }
    }

    public Graph<String, RelationshipEdge> getRelationGraph() { return relationGraph; }
    public List<TimedEvent> getUserTimeline(String userId) {
        return userTimelines.getOrDefault(userId, Collections.emptyList());
    }

    public Map<String, Object> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("vertices", relationGraph.vertexSet().size());
        s.put("edges", relationGraph.edgeSet().size());
        s.put("timelines", userTimelines.size());
        return s;
    }

    private static double toDouble(Object v) { return v instanceof Number ? ((Number) v).doubleValue() : 0; }
}
