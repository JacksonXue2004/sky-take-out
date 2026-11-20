package com.sky.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merchant connections for new-order alerts and customer reminders.
 * <p>
 * Connections open and close on Tomcat's threads while payments broadcast from request threads, so
 * the session map must be safe for concurrent use; a plain HashMap could throw
 * ConcurrentModificationException in the middle of a broadcast. Sessions live in this one process,
 * so with several backend instances a merchant only gets the alerts of the instance it connected to.
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("WebSocket client {} connected", sid);
        sessionMap.put(sid, session);
    }

    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("WebSocket message from client {}: {}", sid, message);
    }

    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("WebSocket client {} disconnected", sid);
        sessionMap.remove(sid);
    }

    /** Never throws: an alert that cannot be delivered must not fail the payment that triggered it. */
    public void sendToAllClient(String message) {
        for (Map.Entry<String, Session> entry : sessionMap.entrySet()) {
            Session session = entry.getValue();
            if (!session.isOpen()) {
                continue;
            }
            try {
                // Tomcat refuses a second send on a session while one is still writing
                // (TEXT_FULL_WRITING), and concurrent payments broadcast at the same time.
                synchronized (session) {
                    session.getBasicRemote().sendText(message);
                }
            } catch (Exception e) {
                log.warn("Could not send an alert to WebSocket client {}: {}", entry.getKey(), e.toString());
            }
        }
    }
}
