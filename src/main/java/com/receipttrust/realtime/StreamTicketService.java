package com.receipttrust.realtime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, single-use tickets for opening the SSE stream.
 *
 * <p>The browser's EventSource can't send an Authorization header, so the stream
 * URL has to carry a credential. Putting the real access token there leaks it
 * into proxy/access logs and browser history. Instead the client first calls an
 * authenticated endpoint to mint a ticket, then opens the stream with it. A
 * ticket works once and expires in {@value #TTL_SECONDS} seconds, so a logged
 * URL is worthless.
 *
 * <p>Tickets are held in memory, which is correct for a single app instance
 * (the current deployment). With multiple instances, move them to a shared store.
 */
@Service
public class StreamTicketService {

    static final long TTL_SECONDS = 30;

    private record Ticket(Long userId, Instant expiresAt) {
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** Mints a ticket bound to the given user. */
    public String issue(Long userId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Ticket(userId, Instant.now().plusSeconds(TTL_SECONDS)));
        return ticket;
    }

    /** Consumes a ticket: returns the user id once if valid and unexpired, else empty. */
    public Optional<Long> consume(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Ticket t = tickets.remove(ticket);
        if (t == null || t.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(t.userId());
    }

    /** Drops expired, never-used tickets so the map can't grow unbounded. */
    @Scheduled(fixedDelay = 60_000)
    void purgeExpired() {
        Instant now = Instant.now();
        tickets.values().removeIf(t -> t.expiresAt().isBefore(now));
    }
}
