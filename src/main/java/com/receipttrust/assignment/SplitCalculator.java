package com.receipttrust.assignment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a monetary amount into N equal shares using integer cents so that the
 * shares always sum back to the original amount exactly. Any indivisible
 * remainder cents are distributed one-per-share to the earliest shares.
 */
public final class SplitCalculator {

    private SplitCalculator() {
    }

    public static List<BigDecimal> equalSplit(BigDecimal total, int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("parts must be positive");
        }
        long totalCents = total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        long base = totalCents / parts;
        long remainder = totalCents - (base * parts);

        List<BigDecimal> shares = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            long cents = base + (i < remainder ? 1 : 0);
            shares.add(BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY));
        }
        return shares;
    }
}
