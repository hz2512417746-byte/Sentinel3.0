package com.antifraud.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 原生 WebSocket 广播：替代 STOMP，直接推送 JSON 日志到前端 */
@Component
public class LogWebSocketHandler extends TextWebSocketHandler {

    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("[WS] 前端连接: " + session.getId() + " (当前 " + sessions.size() + " 个)");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("[WS] 前端断开: " + session.getId() + " (剩余 " + sessions.size() + " 个)");
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        sessions.remove(session);
        System.err.println("[WS] 传输错误: " + ex.getMessage());
    }

    /** 向所有已连接前端广播 JSON */
    public void broadcast(String json) {
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) s.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                sessions.remove(s);
            }
        }
    }

    public int getSessionCount() { return sessions.size(); }
}
