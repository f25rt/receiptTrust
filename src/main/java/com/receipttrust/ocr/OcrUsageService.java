package com.receipttrust.ocr;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

/** Tracks and reports monthly OCR scan usage for quota monitoring. */
@Service
public class OcrUsageService {

    private final OcrUsageRepository repository;

    public OcrUsageService(OcrUsageRepository repository) {
        this.repository = repository;
    }

    public static String currentMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString(); // "YYYY-MM"
    }

    /** Records one scan against the current month. Best-effort; never throws to the caller. */
    @Transactional
    public void recordScan() {
        String ym = currentMonth();
        OcrUsage usage = repository.findByYearMonth(ym).orElseGet(() -> new OcrUsage(ym));
        usage.increment();
        repository.save(usage);
    }

    @Transactional(readOnly = true)
    public int currentMonthCount() {
        return repository.findByYearMonth(currentMonth()).map(OcrUsage::getScanCount).orElse(0);
    }

    @Transactional(readOnly = true)
    public List<OcrUsage> recentMonths() {
        return repository.findTop12ByOrderByYearMonthDesc();
    }
}
