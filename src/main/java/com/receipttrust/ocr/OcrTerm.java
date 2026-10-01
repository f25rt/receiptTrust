package com.receipttrust.ocr;

import com.receipttrust.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A globally confirmed OCR term (store or item name). Captured when users
 * finalize receipts and used to fuzzy-correct future OCR reads.
 */
@Entity
@Table(name = "ocr_terms")
public class OcrTerm extends BaseEntity {

    public enum Kind { STORE, ITEM }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(nullable = false, length = 200)
    private String text;

    @Column(nullable = false, length = 200)
    private String normalized;

    @Column(nullable = false)
    private int occurrences;

    protected OcrTerm() {
    }

    public OcrTerm(Kind kind, String text, String normalized) {
        this.kind = kind;
        this.text = text;
        this.normalized = normalized;
        this.occurrences = 1;
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getNormalized() {
        return normalized;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public void incrementOccurrences() {
        this.occurrences++;
    }
}
