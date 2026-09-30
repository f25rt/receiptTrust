package com.receipttrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "receipttrust.security.jwt")
public class SecurityProperties {

    /** Base64-encoded HMAC-SHA256 signing secret. */
    private String secret;
    private long accessTokenTtlMinutes = 15;
    private long refreshTokenTtlDays = 14;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenTtlMinutes() {
        return accessTokenTtlMinutes;
    }

    public void setAccessTokenTtlMinutes(long accessTokenTtlMinutes) {
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    }

    public long getRefreshTokenTtlDays() {
        return refreshTokenTtlDays;
    }

    public void setRefreshTokenTtlDays(long refreshTokenTtlDays) {
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }
}
