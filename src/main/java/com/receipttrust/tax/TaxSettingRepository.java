package com.receipttrust.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxSettingRepository extends JpaRepository<TaxSetting, Long> {

    Optional<TaxSetting> findByCountryCode(String countryCode);

    boolean existsByCountryCode(String countryCode);

    List<TaxSetting> findAllByOrderByCountryNameAsc();
}
