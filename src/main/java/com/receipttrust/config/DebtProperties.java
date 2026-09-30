package com.receipttrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "receipttrust.debt")
public class DebtProperties {

    private int defaultTermDays = 14;

    public int getDefaultTermDays() {
        return defaultTermDays;
    }

    public void setDefaultTermDays(int defaultTermDays) {
        this.defaultTermDays = defaultTermDays;
    }
}
