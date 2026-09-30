package com.receipttrust.assignment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SplitCalculatorTest {

    @Test
    void splitsEvenlyWhenDivisible() {
        List<BigDecimal> shares = SplitCalculator.equalSplit(new BigDecimal("30.00"), 3);
        assertThat(shares).containsExactly(
                new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("10.00"));
        assertThat(sum(shares)).isEqualByComparingTo("30.00");
    }

    @Test
    void distributesRemainderCentsToEarliestShares() {
        List<BigDecimal> shares = SplitCalculator.equalSplit(new BigDecimal("10.00"), 3);
        // 1000 cents / 3 -> 334, 333, 333
        assertThat(shares).containsExactly(
                new BigDecimal("3.34"), new BigDecimal("3.33"), new BigDecimal("3.33"));
        assertThat(sum(shares)).isEqualByComparingTo("10.00");
    }

    @Test
    void sumAlwaysEqualsTotalForOddAmounts() {
        List<BigDecimal> shares = SplitCalculator.equalSplit(new BigDecimal("7.01"), 4);
        assertThat(sum(shares)).isEqualByComparingTo("7.01");
        assertThat(shares).hasSize(4);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
