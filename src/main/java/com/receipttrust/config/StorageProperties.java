package com.receipttrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "receipttrust.storage")
public class StorageProperties {

    private String receiptDir = "./storage/receipts";
    private String profileDir = "./storage/profiles";

    public String getReceiptDir() {
        return receiptDir;
    }

    public void setReceiptDir(String receiptDir) {
        this.receiptDir = receiptDir;
    }

    public String getProfileDir() {
        return profileDir;
    }

    public void setProfileDir(String profileDir) {
        this.profileDir = profileDir;
    }
}
