package com.receipttrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls automatic deletion of uploaded images. Intended for the testing phase
 * to keep disk usage low; turn OFF for live so images are retained.
 *
 * enabled=true  -> testing: sweep deletes expired receipt/profile images
 * enabled=false -> live: images are kept (no deletion)
 */
@ConfigurationProperties(prefix = "receipttrust.image-expiry")
public class ImageExpiryProperties {

    /** Master switch. false = live (keep images), true = testing (delete on expiry). */
    private boolean enabled = false;

    /** Receipt image lifetime in seconds (default 60 = 1 minute). */
    private long receiptTtlSeconds = 60;

    /** Profile image lifetime in seconds (default 3600 = 1 hour). */
    private long profileTtlSeconds = 3600;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getReceiptTtlSeconds() {
        return receiptTtlSeconds;
    }

    public void setReceiptTtlSeconds(long receiptTtlSeconds) {
        this.receiptTtlSeconds = receiptTtlSeconds;
    }

    public long getProfileTtlSeconds() {
        return profileTtlSeconds;
    }

    public void setProfileTtlSeconds(long profileTtlSeconds) {
        this.profileTtlSeconds = profileTtlSeconds;
    }
}
