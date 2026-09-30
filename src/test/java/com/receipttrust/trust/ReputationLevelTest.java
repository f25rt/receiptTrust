package com.receipttrust.trust;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReputationLevelTest {

    @Test
    void mapsScoreRangesToLevels() {
        assertThat(ReputationLevel.fromScore(300)).isEqualTo(ReputationLevel.POOR);
        assertThat(ReputationLevel.fromScore(449)).isEqualTo(ReputationLevel.POOR);
        assertThat(ReputationLevel.fromScore(450)).isEqualTo(ReputationLevel.FAIR);
        assertThat(ReputationLevel.fromScore(549)).isEqualTo(ReputationLevel.FAIR);
        assertThat(ReputationLevel.fromScore(550)).isEqualTo(ReputationLevel.GOOD);
        assertThat(ReputationLevel.fromScore(620)).isEqualTo(ReputationLevel.GOOD);
        assertThat(ReputationLevel.fromScore(650)).isEqualTo(ReputationLevel.VERY_GOOD);
        assertThat(ReputationLevel.fromScore(749)).isEqualTo(ReputationLevel.VERY_GOOD);
        assertThat(ReputationLevel.fromScore(750)).isEqualTo(ReputationLevel.EXCELLENT);
        assertThat(ReputationLevel.fromScore(850)).isEqualTo(ReputationLevel.EXCELLENT);
    }
}
