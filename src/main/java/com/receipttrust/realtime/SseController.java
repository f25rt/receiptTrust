package com.receipttrust.realtime;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.security.JwtService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Opens the per-user Server-Sent Events stream. The browser's EventSource API
 * cannot send an Authorization header, so the access token is passed as a query
 * parameter and validated here directly.
 */
@RestController
public class SseController {

    private final JwtService jwtService;
    private final SseService sseService;

    public SseController(JwtService jwtService, SseService sseService) {
        this.jwtService = jwtService;
        this.sseService = sseService;
    }

    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("token") String token) {
        Long userId;
        try {
            userId = jwtService.parseUserId(token);
        } catch (Exception e) {
            throw new ApiExceptions.ForbiddenException("Invalid or expired token");
        }
        return sseService.register(userId);
    }
}
