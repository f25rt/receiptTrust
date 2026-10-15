package com.receipttrust.tax;

import com.receipttrust.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Per-country VAT/tax configuration managed by admins.
 *
 * <p>{@code rate} is a percentage (e.g. {@code 12.000} for 12%). {@code inclusive}
 * tells the client whether the tax is already baked into listed prices
 * (so it should NOT be added to the total) or added on top at checkout
 * (so it SHOULD be added).
 */
@Entity
@Table(name = "tax_settings")
public class TaxSetting extends BaseEntity {

    /** ISO 3166-1 alpha-2 country code, upper-cased (e.g. "PH"). Unique. */
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "country_name", nullable = false, length = 100)
    private String countryName;

    @Column(name = "rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal rate = BigDecimal.ZERO;

    @Column(name = "inclusive", nullable = false)
    private boolean inclusive = true;

    protected TaxSetting() {
    }

    public TaxSetting(String countryCode, String countryName, BigDecimal rate, boolean inclusive) {
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.rate = rate;
        this.inclusive = inclusive;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public boolean isInclusive() {
        return inclusive;
    }

    public void setInclusive(boolean inclusive) {
        this.inclusive = inclusive;
    }
}
