package com.receipttrust.security;

import com.receipttrust.config.SecurityProperties;
import com.receipttrust.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final long ttlDays;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties) {
        this.repository = repository;
        this.ttlDays = properties.getRefreshTokenTtlDays();
    }

    @Transactional
    public RefreshToken issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(ttlDays, ChronoUnit.DAYS);
        return repository.save(new RefreshToken(user, token, expiresAt));
    }

    @Transactional(readOnly = true)
    public RefreshToken validate(String token) {
        RefreshToken stored = repository.findByToken(token).orElse(null);
        if (stored == null || !stored.isActive()) {
            return null;
        }
        return stored;
    }

    @Transactional
    public void revoke(String token) {
        repository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            repository.save(rt);
        });
    }
}
