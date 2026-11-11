package com.receipttrust.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Per-client-IP rate limit on the unauthenticated auth endpoints (login,
 * register, refresh, social sign-in) to blunt credential stuffing and
 * account-creation spam. Per-account lockout for failed logins is handled
 * separately by {@link LoginAttemptLimiter}.
 *
 * <p>Behind a reverse proxy the client IP comes from X-Forwarded-For. That
 * header is client-controllable, so the IP limit alone can be dodged by
 * rotating it; the per-username limiter is what protects individual accounts.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private record Rule(int max, long windowMs) {
    }

    private static final long MINUTE = 60_000L;

    private static final Map<String, Rule> RULES = Map.of(
            "/api/auth/login", new Rule(20, 5 * MINUTE),
            "/api/auth/register", new Rule(10, 60 * MINUTE),
            "/api/auth/refresh", new Rule(60, 5 * MINUTE),
            "/api/auth/google", new Rule(20, 5 * MINUTE),
            "/api/auth/facebook", new Rule(20, 5 * MINUTE));

    private final RateLimiter limiter;
    private final boolean enabled;

    public AuthRateLimitFilter(RateLimiter limiter,
                               @Value("${receipttrust.rate-limit.enabled:true}") boolean enabled) {
        this.limiter = limiter;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !enabled
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !RULES.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        Rule rule = RULES.get(path);
        long wait = limiter.acquire("ip:" + path + ":" + clientIp(request), rule.max(), rule.windowMs());
        if (wait > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(wait));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Too many requests. Please try again in " + wait + " seconds.\","
                    + "\"path\":\"" + path + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
