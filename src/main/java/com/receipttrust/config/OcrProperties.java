package com.receipttrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "receipttrust.ocr")
public class OcrProperties {

    /** Whether OCR is enabled. When false, the scan endpoint returns an empty draft. */
    private boolean enabled = true;

    /** Path to the tessdata directory containing <lang>.traineddata. */
    private String dataPath = "./tessdata";

    /** Tesseract language(s), e.g. "eng". */
    private String language = "eng";

    /** OCR engine to use: "tesseract" (default, free, local) or "google" (Cloud Vision). */
    private String provider = "tesseract";

    /** Google Cloud Vision API key (only needed when provider = google). */
    private String visionApiKey = "";

    /** OCR.space API key (only needed when provider = ocrspace). */
    private String ocrspaceApiKey = "";

    public String getOcrspaceApiKey() {
        return ocrspaceApiKey;
    }

    public void setOcrspaceApiKey(String ocrspaceApiKey) {
        this.ocrspaceApiKey = ocrspaceApiKey;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getVisionApiKey() {
        return visionApiKey;
    }

    public void setVisionApiKey(String visionApiKey) {
        this.visionApiKey = visionApiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
