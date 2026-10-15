package com.receipttrust.security;

import com.receipttrust.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMinutes;

    public JwtService(SecurityProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.getSecret());
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtlMinutes = properties.getAccessTokenTtlMinutes();
    }

    public String generateAccessToken(Long userId, String username, int sessionVersion) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTtlMinutes * 60);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("sv", sessionVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates the token signature and expiry.
     *
     * @return the user id encoded in the subject
     * @throws io.jsonwebtoken.JwtException if invalid or expired
     */
    public Long parseUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    /** The session version embedded in the token (0 if absent, for old tokens). */
    public int parseSessionVersion(String token) {
        Integer sv = parseClaims(token).get("sv", Integer.class);
        return sv == null ? 0 : sv;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
