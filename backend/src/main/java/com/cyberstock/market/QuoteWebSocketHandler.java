package com.cyberstock.market;

import com.cyberstock.instrument.InstrumentService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-time quote stream at {@code /ws/quotes}.
 *
 * <pre>
 * client → {"action":"subscribe","symbols":["AAPL","0700.HK"]}   ("*" subscribes to all)
 * client → {"action":"unsubscribe","symbols":["AAPL"]}
 * client → {"action":"ping"}
 * server → {"type":"snapshot","data":[Quote...]}
 * server → {"type":"quote","data":Quote}
 * server → {"type":"pong","ts":1700000000000}
 * </pre>
 */
@Component
public class QuoteWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(QuoteWebSocketHandler.class);
    private static final int MAX_SYMBOLS_PER_SESSION = 200;
    private static final String ALL = "*";

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final QuoteService quotes;

    public QuoteWebSocketHandler(ObjectMapper mapper, QuoteService quotes) {
        this.mapper = mapper;
        this.quotes = quotes;
        quotes.addListener(this::broadcast);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Decorator serialises concurrent sends and drops slow clients (buffer > 512 KB)
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5_000, 512 * 1024));
        subscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (RuntimeException e) {
            return; // ignore malformed frames
        }
        String action = node.path("action").asString("");
        Set<String> subs = subscriptions.get(session.getId());
        if (subs == null) {
            return;
        }
        switch (action) {
            case "subscribe" -> {
                node.path("symbols").forEach(s -> {
                    if (subs.size() < MAX_SYMBOLS_PER_SESSION) {
                        String sym = s.asString("");
                        subs.add(ALL.equals(sym) ? ALL : InstrumentService.normalize(sym));
                    }
                });
                List<Quote> snapshot = subs.contains(ALL) ? List.copyOf(quotes.all()) : quotes.latest(subs);
                send(session.getId(), Map.of("type", "snapshot", "data", snapshot));
            }
            case "unsubscribe" -> node.path("symbols").forEach(s -> {
                String sym = s.asString("");
                subs.remove(ALL.equals(sym) ? ALL : InstrumentService.normalize(sym));
            });
            case "ping" -> send(session.getId(), Map.of("type", "pong", "ts", System.currentTimeMillis()));
            default -> { /* unknown action: ignore */ }
        }
    }

    void broadcast(Quote q) {
        if (sessions.isEmpty()) {
            return;
        }
        String payload = mapper.writeValueAsString(Map.of("type", "quote", "data", q));
        subscriptions.forEach((id, subs) -> {
            if (subs.contains(ALL) || subs.contains(q.symbol())) {
                sendRaw(id, payload);
            }
        });
    }

    private void send(String id, Object body) {
        sendRaw(id, mapper.writeValueAsString(body));
    }

    private void sendRaw(String id, String payload) {
        WebSocketSession s = sessions.get(id);
        if (s == null || !s.isOpen()) {
            return;
        }
        try {
            s.sendMessage(new TextMessage(payload));
        } catch (IOException | RuntimeException e) {
            log.debug("Dropping WebSocket session {}: {}", id, e.getMessage());
            remove(id);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session.getId());
    }

    private void remove(String id) {
        WebSocketSession s = sessions.remove(id);
        subscriptions.remove(id);
        if (s != null && s.isOpen()) {
            try {
                s.close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (IOException ignored) {
                // already closing
            }
        }
    }

    public int sessionCount() {
        return sessions.size();
    }
}
