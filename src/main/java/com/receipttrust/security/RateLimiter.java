package com.receipttrust.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory sliding-window rate limiter keyed by an arbitrary string
 * (client IP, username, ...). Each key keeps the timestamps of its recent hits;
 * a hit is allowed while fewer than {@code max} fall inside the window.
 *
 * <p>State is per application instance, which matches the current single-instance
 * deployment. With several instances, back this with a shared store (e.g. Redis).
 */
@Component
public class RateLimiter {

    /** Entries idle longer than this are dropped by the periodic purge. */
    private static final long IDLE_PURGE_MS = 2L * 60 * 60 * 1000;

    private final ConcurrentHashMap<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * Records a hit if the key is under its limit.
     *
     * @return 0 if allowed (and counted), otherwise the seconds until a slot frees up
     */
    public long acquire(String key, int max, long windowMs) {
        long now = System.currentTimeMillis();
        long[] retryAfter = {0};
        hits.compute(key, (k, q) -> {
            ArrayDeque<Long> deque = q == null ? new ArrayDeque<>() : q;
            prune(deque, now, windowMs);
            if (deque.size() >= max) {
                retryAfter[0] = secondsUntilFree(deque, now, windowMs);
            } else {
                deque.addLast(now);
            }
            return deque;
        });
        return retryAfter[0];
    }

    /** Checks the limit without counting a hit. @return 0 if under the limit, else seconds to wait */
    public long retryAfter(String key, int max, long windowMs) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> deque = hits.get(key);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            prune(deque, now, windowMs);
            return deque.size() >= max ? secondsUntilFree(deque, now, windowMs) : 0;
        }
    }

    /** Counts a hit unconditionally (used for failed-login tracking). */
    public void record(String key, long windowMs) {
        long now = System.currentTimeMillis();
        hits.compute(key, (k, q) -> {
            ArrayDeque<Long> deque = q == null ? new ArrayDeque<>() : q;
            prune(deque, now, windowMs);
            deque.addLast(now);
            return deque;
        });
    }

    public void reset(String key) {
        hits.remove(key);
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    void purgeIdle() {
        long cutoff = System.currentTimeMillis() - IDLE_PURGE_MS;
        hits.entrySet().removeIf(e -> {
            ArrayDeque<Long> d = e.getValue();
            synchronized (d) {
                return d.isEmpty() || d.peekLast() < cutoff;
            }
        });
    }

    private static void prune(ArrayDeque<Long> deque, long now, long windowMs) {
        while (!deque.isEmpty() && deque.peekFirst() <= now - windowMs) {
            deque.pollFirst();
        }
    }

    private static long secondsUntilFree(ArrayDeque<Long> deque, long now, long windowMs) {
        long freeAt = deque.peekFirst() + windowMs;
        return Math.max(1, (freeAt - now + 999) / 1000);
    }
}
