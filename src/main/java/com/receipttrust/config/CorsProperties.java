package com.receipttrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "receipttrust.cors")
public class CorsProperties {

    /** Allowed origins for browser clients. Configure per environment. */
    private List<String> allowedOrigins = List.of("http://localhost:5173", "http://localhost:5174");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
