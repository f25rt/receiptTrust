package com.receipttrust.ocr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OcrUsageRepository extends JpaRepository<OcrUsage, Long> {

    Optional<OcrUsage> findByYearMonth(String yearMonth);

    List<OcrUsage> findTop12ByOrderByYearMonthDesc();
}
