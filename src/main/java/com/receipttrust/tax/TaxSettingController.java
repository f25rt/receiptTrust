package com.receipttrust.tax;

import com.receipttrust.security.CurrentUserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Country tax/VAT settings. Listing is open to any authenticated user (the
 * receipt page reads it to pre-fill rates); create/update/delete require ADMIN,
 * enforced in {@link TaxSettingService}.
 */
@RestController
@RequestMapping("/api/tax-settings")
public class TaxSettingController {

    private final TaxSettingService taxSettingService;
    private final CurrentUserService currentUserService;

    public TaxSettingController(TaxSettingService taxSettingService,
                                CurrentUserService currentUserService) {
        this.taxSettingService = taxSettingService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<TaxDtos.TaxSettingDto> list() {
        // Any authenticated user may read tax settings.
        currentUserService.require();
        return taxSettingService.list();
    }

    @PostMapping
    public TaxDtos.TaxSettingDto save(@RequestBody TaxDtos.TaxSettingRequest request) {
        return taxSettingService.save(currentUserService.require(), request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        taxSettingService.delete(currentUserService.require(), id);
    }
}
