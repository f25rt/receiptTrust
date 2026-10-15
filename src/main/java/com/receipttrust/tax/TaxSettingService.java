package com.receipttrust.tax;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Manages per-country VAT/tax settings. Reads are public (any authenticated
 * user, so the receipt page can pre-fill rates); writes require ADMIN.
 */
@Service
public class TaxSettingService {

    private static final BigDecimal MAX_RATE = new BigDecimal("100");

    private final TaxSettingRepository repository;

    public TaxSettingService(TaxSettingRepository repository) {
        this.repository = repository;
    }

    private void requireAdmin(User user) {
        if (user == null || !user.isAdmin()) {
            throw new ApiExceptions.ForbiddenException("Admin access required");
        }
    }

    @Transactional(readOnly = true)
    public List<TaxDtos.TaxSettingDto> list() {
        return repository.findAllByOrderByCountryNameAsc().stream()
                .map(TaxSettingService::toDto)
                .toList();
    }

    /** Public country options (code + name) for the registration dropdown. */
    @Transactional(readOnly = true)
    public List<TaxDtos.CountryOption> countryOptions() {
        return repository.findAllByOrderByCountryNameAsc().stream()
                .map(t -> new TaxDtos.CountryOption(t.getCountryCode(), t.getCountryName()))
                .toList();
    }

    /**
     * Resolves the tax setting that applies to the given user from their country.
     * Returns a {@code configured=false} result when the country is unknown/unset
     * so the client can simply fall back to no tax.
     */
    @Transactional(readOnly = true)
    public TaxDtos.MyTaxSetting resolveForUser(User user) {
        String code = user.getCountry() == null ? null : user.getCountry().trim().toUpperCase();
        TaxSetting setting = code == null ? null
                : repository.findByCountryCode(code).orElse(null);
        if (setting == null) {
            return new TaxDtos.MyTaxSetting(
                    code, null, BigDecimal.ZERO, true, user.isTaxEnabled(), false);
        }
        return new TaxDtos.MyTaxSetting(
                setting.getCountryCode(), setting.getCountryName(), setting.getRate(),
                setting.isInclusive(), user.isTaxEnabled(), true);
    }

    /** Creates a country or updates it when the code already exists (upsert by code). */
    @Transactional
    public TaxDtos.TaxSettingDto save(User admin, TaxDtos.TaxSettingRequest req) {
        requireAdmin(admin);

        String code = normalizeCode(req.countryCode());
        String name = req.countryName() == null ? "" : req.countryName().trim();
        if (name.isEmpty()) {
            throw new ApiExceptions.ValidationException("Country name is required");
        }
        BigDecimal rate = req.rate() == null ? BigDecimal.ZERO : req.rate();
        if (rate.signum() < 0 || rate.compareTo(MAX_RATE) > 0) {
            throw new ApiExceptions.ValidationException("Tax rate must be between 0 and 100");
        }
        boolean inclusive = req.inclusive() == null || req.inclusive();

        TaxSetting entity = repository.findByCountryCode(code).orElseGet(TaxSetting::new);
        entity.setCountryCode(code);
        entity.setCountryName(name);
        entity.setRate(rate);
        entity.setInclusive(inclusive);
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(User admin, Long id) {
        requireAdmin(admin);
        TaxSetting entity = repository.findById(id)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Tax setting not found"));
        repository.delete(entity);
    }

    private String normalizeCode(String code) {
        if (code == null || code.trim().length() != 2) {
            throw new ApiExceptions.ValidationException("Country code must be 2 letters (e.g. PH)");
        }
        String normalized = code.trim().toUpperCase();
        if (!normalized.chars().allMatch(Character::isLetter)) {
            throw new ApiExceptions.ValidationException("Country code must be 2 letters (e.g. PH)");
        }
        return normalized;
    }

    private static TaxDtos.TaxSettingDto toDto(TaxSetting t) {
        return new TaxDtos.TaxSettingDto(
                t.getId(), t.getCountryCode(), t.getCountryName(), t.getRate(), t.isInclusive());
    }
}
