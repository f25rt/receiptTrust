package com.receipttrust.ocr;

import com.receipttrust.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Running count of OCR scans in a calendar month (YYYY-MM), for quota monitoring. */
@Entity
@Table(name = "ocr_usage")
public class OcrUsage extends BaseEntity {

    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "scan_count", nullable = false)
    private int scanCount;

    protected OcrUsage() {
    }

    public OcrUsage(String yearMonth) {
        this.yearMonth = yearMonth;
        this.scanCount = 0;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public int getScanCount() {
        return scanCount;
    }

    public void increment() {
        this.scanCount++;
    }
}
