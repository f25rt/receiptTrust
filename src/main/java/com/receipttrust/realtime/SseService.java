package com.receipttrust.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps a live Server-Sent Events connection per user and pushes small event
 * notifications ("something changed, go refresh X") to the browser so the UI
 * updates without polling. A user may have several connections (multiple tabs);
 * each is tracked and cleaned up on completion, timeout, or error.
 */
@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);

    /** Long timeout so the browser holds one connection; EventSource reconnects if it drops. */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Registers a new connection for the user and wires up cleanup. */
    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        // Initial event so the client knows the stream is live.
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /**
     * Pushes an event to every open connection for a user. {@code type} is the
     * SSE event name the frontend listens for (e.g. "notification", "message",
     * "assignment"); {@code payload} is a short string the client can use or ignore.
     */
    public void publish(Long userId, String type, String payload) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(type).data(payload == null ? "" : payload));
            } catch (Exception e) {
                remove(userId, emitter);
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }

    /** Periodic heartbeat keeps intermediaries from closing idle connections. */
    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(userId, emitter);
                }
            }
        });
    }
}
