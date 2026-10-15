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

    /** Public, minimal country option for the registration dropdown. */
    public record CountryOption(String countryCode, String countryName) {
    }

    /**
     * The tax configuration that applies to the current user, resolved from
     * their country. {@code enabled} reflects the user's own on/off preference;
     * {@code configured} is false when the user's country has no tax setting.
     */
    public record MyTaxSetting(
            String countryCode,
            String countryName,
            BigDecimal rate,
            boolean inclusive,
            boolean enabled,
            boolean configured
    ) {
    }
}
