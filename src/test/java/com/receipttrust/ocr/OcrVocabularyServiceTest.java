package com.receipttrust.ocr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OcrVocabularyServiceTest {

    @Test
    void normalizeLowercasesAndCollapsesPunctuation() {
        assertThat(OcrVocabularyService.normalize("  Double - Thigh!! ")).isEqualTo("double thigh");
        assertThat(OcrVocabularyService.normalize("SUNBURST")).isEqualTo("sunburst");
        assertThat(OcrVocabularyService.normalize(null)).isEqualTo("");
    }

    @Test
    void similarityIsHighForCloseStringsAndLowForDifferent() {
        // Same string -> 1.0
        assertThat(OcrVocabularyService.similarity("sunburst", "sunburst")).isEqualTo(1.0);
        // One-char OCR slip stays well above the 0.72 match threshold.
        assertThat(OcrVocabularyService.similarity("sunbvrst", "sunburst")).isGreaterThan(0.72);
        // Unrelated strings score low.
        assertThat(OcrVocabularyService.similarity("plain rice", "double thigh")).isLessThan(0.5);
    }
}
