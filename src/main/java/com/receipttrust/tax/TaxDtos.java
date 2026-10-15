package com.receipttrust.tax;

import java.math.BigDecimal;

public final class TaxDtos {

    private TaxDtos() {
    }

    /** A country's tax configuration as returned to clients. */
    public record TaxSettingDto(
            Long id,
            String countryCode,
            String countryName,
            BigDecimal rate,
            boolean inclusive
    ) {
    }

    /**
     * Create/update payload. {@code countryCode} identifies the row (upsert by
     * code); {@code id} is ignored on write and present only for round-tripping.
     */
    public record TaxSettingRequest(
            String countryCode,
            String countryName,
            BigDecimal rate,
            Boolean inclusive
    ) {
    }
}
