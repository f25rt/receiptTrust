package com.receipttrust.security;

import com.receipttrust.common.exception.ApiExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Per-account brute-force protection for password login. Counts FAILED attempts
 * per username; once the cap is hit, further attempts are rejected (429) until
 * the window passes, even if they come from different IPs. A successful login
 * clears the counter.
 *
 * <p>Tracks attempts for unknown usernames too, so the lockout behavior doesn't
 * reveal which usernames exist.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 8;
    static final long WINDOW_MS = 15L * 60 * 1000;

    private final RateLimiter limiter;
    private final boolean enabled;

    public LoginAttemptLimiter(RateLimiter limiter,
                               @Value("${receipttrust.rate-limit.enabled:true}") boolean enabled) {
        this.limiter = limiter;
        this.enabled = enabled;
    }

    /** Throws 429 if this username has too many recent failures. */
    public void check(String username) {
        if (!enabled) {
            return;
        }
        long wait = limiter.retryAfter(key(username), MAX_FAILURES, WINDOW_MS);
        if (wait > 0) {
            throw new ApiExceptions.TooManyRequestsException(
                    "Too many failed sign-in attempts. Try again in " + humanize(wait) + ".", wait);
        }
    }

    public void recordFailure(String username) {
        if (enabled) {
            limiter.record(key(username), WINDOW_MS);
        }
    }

    public void reset(String username) {
        limiter.reset(key(username));
    }

    private static String key(String username) {
        return "login-user:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String humanize(long seconds) {
        return seconds >= 120 ? (seconds + 59) / 60 + " minutes" : seconds + " seconds";
    }
}
