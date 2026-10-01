package com.receipttrust.ocr;

/**
 * Extracts raw text from an image. Implementations are selected at runtime by
 * {@code receipttrust.ocr.provider}. The returned text is fed to
 * {@link ReceiptTextParser}; an engine returns null/blank when it cannot read
 * the image, and the caller falls back to manual entry.
 */
public interface OcrEngine {

    /** A stable id matching the configured provider (e.g. "tesseract", "google"). */
    String id();

    /** True when this engine is usable (native lib present, API key set, etc.). */
    boolean isAvailable();

    /** Extracts text from the given image bytes, or null if it cannot. */
    String extractText(byte[] imageBytes);
}
