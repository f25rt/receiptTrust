package com.receipttrust.security;

import com.receipttrust.config.SecurityProperties;
import com.receipttrust.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and validates refresh tokens. The database stores only a SHA-256 hash;
 * the raw token is returned to the client once and never persisted. Tokens are
 * 256 bits of CSPRNG output, so an unsalted fast hash is sufficient (there is
 * nothing to brute-force, unlike a human-chosen password).
 */
@Service
public class RefreshTokenService {

    /** A freshly issued token: the stored entity plus the raw value to give the client. */
    public record IssuedToken(RefreshToken entity, String rawToken) {
    }

    private final RefreshTokenRepository repository;
    private final long ttlDays;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties) {
        this.repository = repository;
        this.ttlDays = properties.getRefreshTokenTtlDays();
    }

    @Transactional
    public IssuedToken issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(ttlDays, ChronoUnit.DAYS);
        RefreshToken saved = repository.save(new RefreshToken(user, hash(raw), expiresAt));
        return new IssuedToken(saved, raw);
    }

    @Transactional(readOnly = true)
    public RefreshToken validate(String token) {
        RefreshToken stored = find(token).orElse(null);
        if (stored == null || !stored.isActive()) {
            return null;
        }
        return stored;
    }

    @Transactional
    public void revoke(String token) {
        find(token).ifPresent(rt -> {
            rt.setRevoked(true);
            repository.save(rt);
        });
    }

    /** Revokes all of a user's active refresh tokens (new login = single device). */
    @Transactional
    public void revokeAllForUser(User user) {
        repository.revokeAllForUser(user);
    }

    /** The user that owns a refresh token, if it exists. */
    @Transactional(readOnly = true)
    public Optional<User> ownerOf(String token) {
        return find(token).map(RefreshToken::getUser);
    }

    private Optional<RefreshToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(rawToken));
    }

    /** Hex-encoded SHA-256 of the raw token. */
    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required to exist on every JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
