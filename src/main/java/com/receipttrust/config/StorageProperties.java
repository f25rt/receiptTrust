package com.receipttrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "receipttrust.storage")
public class StorageProperties {

    private String receiptDir = "./storage/receipts";
    private String profileDir = "./storage/profiles";

    /**
     * When false (default), uploaded receipt images are OCR'd but NOT persisted —
     * the debt is backed by its extracted itemized data instead of the photo.
     * Set true to store the image (e.g. with real object storage or a disk).
     */
    private boolean persistReceiptImage = false;

    public boolean isPersistReceiptImage() {
        return persistReceiptImage;
    }

    public void setPersistReceiptImage(boolean persistReceiptImage) {
        this.persistReceiptImage = persistReceiptImage;
    }

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
