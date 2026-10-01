package com.receipttrust.ocr;

import com.receipttrust.config.OcrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates receipt OCR: selects the configured engine (Tesseract by default,
 * optionally Google Cloud Vision), extracts text, parses it into a draft, and
 * snaps store/item names to the learned vocabulary. Degrades gracefully to an
 * empty draft (manual entry) whenever OCR is disabled, unavailable, or unreliable.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final OcrProperties properties;
    private final OcrVocabularyService vocabulary;
    private final List<OcrEngine> engines;

    public OcrService(OcrProperties properties, OcrVocabularyService vocabulary, List<OcrEngine> engines) {
        this.properties = properties;
        this.vocabulary = vocabulary;
        this.engines = engines;
    }

    public OcrDtos.ReceiptDraft scan(MultipartFile file) {
        if (!properties.isEnabled()) {
            return OcrDtos.ReceiptDraft.empty();
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            // PDFs and non-images are not OCR'd in this MVP; manual entry follows.
            return OcrDtos.ReceiptDraft.empty();
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.warn("Failed to read uploaded image bytes: {}", e.getMessage());
            return OcrDtos.ReceiptDraft.empty();
        }

        OcrEngine engine = selectEngine();
        if (engine == null) {
            log.warn("No OCR engine available; returning empty draft");
            return OcrDtos.ReceiptDraft.empty();
        }

        String text = engine.extractText(bytes);
        log.info("OCR engine '{}' read {} chars", engine.id(), text == null ? 0 : text.length());
        if (text != null) {
            String snippet = text.replaceAll("\\s+", " ").strip();
            log.info("OCR raw text (first 300 chars): {}",
                    snippet.length() > 300 ? snippet.substring(0, 300) : snippet);
        }
        if (looksLikeGibberish(text)) {
            log.info("OCR output looks unreliable; returning empty draft for manual entry");
            return OcrDtos.ReceiptDraft.empty();
        }
        return applyVocabulary(ReceiptTextParser.parse(text));
    }

    /**
     * Chooses the configured provider's engine; if it's unavailable (e.g. Google
     * selected but no API key), falls back to any other available engine.
     */
    private OcrEngine selectEngine() {
        String provider = properties.getProvider() == null ? "tesseract"
                : properties.getProvider().trim().toLowerCase();
        OcrEngine selected = null;
        for (OcrEngine e : engines) {
            if (e.id().equals(provider)) {
                selected = e;
                break;
            }
        }
        if (selected != null && selected.isAvailable()) {
            return selected;
        }
        if (selected != null) {
            log.warn("Configured OCR provider '{}' is not available; falling back", provider);
        }
        for (OcrEngine e : engines) {
            if (e.isAvailable()) {
                return e;
            }
        }
        return null;
    }

    /** Snaps parsed store/item names to known confirmed terms (learning vocabulary). */
    private OcrDtos.ReceiptDraft applyVocabulary(OcrDtos.ReceiptDraft draft) {
        String store = draft.storeName();
        if (store != null) {
            store = vocabulary.correct(OcrTerm.Kind.STORE, store).orElse(store);
        }
        List<OcrDtos.ParsedItem> items = new ArrayList<>();
        for (OcrDtos.ParsedItem item : draft.items()) {
            String name = vocabulary.correct(OcrTerm.Kind.ITEM, item.name()).orElse(item.name());
            items.add(new OcrDtos.ParsedItem(name, item.quantity(), item.unitPrice()));
        }
        return new OcrDtos.ReceiptDraft(store, items, draft.serviceCharge(), draft.tax(), draft.total(), draft.rawText());
    }

    /**
     * Detects unusable OCR output (random letters/noise) so we fall back to clean
     * manual entry instead of showing gibberish. Flags text with very few real
     * words (letter-runs of length >= 3).
     */
    private boolean looksLikeGibberish(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String[] tokens = text.split("\\s+");
        int realWords = 0;
        int alphaTokens = 0;
        for (String t : tokens) {
            String letters = t.replaceAll("[^A-Za-z]", "");
            if (letters.length() >= 2) {
                alphaTokens++;
            }
            if (letters.length() >= 3) {
                realWords++;
            }
        }
        if (realWords < 2) {
            return true;
        }
        return alphaTokens > 0 && ((double) realWords / alphaTokens) < 0.25;
    }
}
