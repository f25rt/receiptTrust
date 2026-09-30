package com.receipttrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "receipttrust.trust")
public class TrustProperties {

    private int initialScore = 500;
    private int minScore = 300;
    private int maxScore = 850;

    public int getInitialScore() {
        return initialScore;
    }

    public void setInitialScore(int initialScore) {
        this.initialScore = initialScore;
    }

    public int getMinScore() {
        return minScore;
    }

    public void setMinScore(int minScore) {
        this.minScore = minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = maxScore;
    }
}
