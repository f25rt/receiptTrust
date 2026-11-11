package com.receipttrust.realtime;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.security.CurrentUserService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Opens the per-user Server-Sent Events stream. The browser's EventSource API
 * cannot send an Authorization header, so the stream is opened with a
 * short-lived, single-use ticket (minted by the authenticated
 * {@code POST /api/stream-ticket}) rather than the access token itself.
 */
@RestController
public class SseController {

    private final StreamTicketService ticketService;
    private final SseService sseService;
    private final CurrentUserService currentUserService;

    public SseController(StreamTicketService ticketService,
                         SseService sseService,
                         CurrentUserService currentUserService) {
        this.ticketService = ticketService;
        this.sseService = sseService;
        this.currentUserService = currentUserService;
    }

    /** Authenticated: mints a one-time ticket for opening the stream. */
    @PostMapping("/api/stream-ticket")
    public Map<String, String> ticket() {
        return Map.of("ticket", ticketService.issue(currentUserService.require().getId()));
    }

    /** Public path (EventSource can't send headers); authorized by the ticket. */
    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(value = "ticket", required = false) String ticket) {
        Long userId = ticketService.consume(ticket)
                .orElseThrow(() -> new ApiExceptions.ForbiddenException("Invalid or expired ticket"));
        return sseService.register(userId);
    }
}
