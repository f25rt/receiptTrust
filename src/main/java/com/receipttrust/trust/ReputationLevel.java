package com.receipttrust.trust;

public enum ReputationLevel {
    POOR,
    FAIR,
    GOOD,
    VERY_GOOD,
    EXCELLENT;

    public static ReputationLevel fromScore(int score) {
        if (score < 450) {
            return POOR;
        } else if (score < 550) {
            return FAIR;
        } else if (score < 650) {
            return GOOD;
        } else if (score < 750) {
            return VERY_GOOD;
        }
        return EXCELLENT;
    }
}
