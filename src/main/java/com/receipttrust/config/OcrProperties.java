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
