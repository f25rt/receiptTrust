package com.receipttrust.ocr;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Global OCR learning vocabulary. Confirmed store/item names are recorded here,
 * and future OCR reads are fuzzy-matched against them so recognition improves as
 * more receipts are confirmed — without retraining Tesseract.
 */
@Service
public class OcrVocabularyService {

    /** Minimum similarity (0..1) for an OCR string to be snapped to a known term. */
    private static final double MATCH_THRESHOLD = 0.72;

    private final OcrTermRepository repository;

    public OcrVocabularyService(OcrTermRepository repository) {
        this.repository = repository;
    }

    /** Normalizes a term for matching: lowercase, collapse non-alphanumerics to single spaces. */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .strip();
    }

    /** Records a confirmed term, incrementing its occurrence count if it already exists. */
    @Transactional
    public void record(OcrTerm.Kind kind, String text) {
        if (text == null) {
            return;
        }
        String clean = text.strip();
        String norm = normalize(clean);
        // Ignore too-short or non-alphabetic noise.
        if (norm.length() < 2 || norm.chars().noneMatch(Character::isLetter)) {
            return;
        }
        repository.findByKindAndNormalized(kind, norm).ifPresentOrElse(
                existing -> {
                    existing.incrementOccurrences();
                    repository.save(existing);
                },
                () -> repository.save(new OcrTerm(kind, clean, norm)));
    }

    /**
     * Returns the best-matching known term for an OCR string, if similarity is
     * above the threshold; otherwise empty (keep the OCR text as-is).
     */
    @Transactional(readOnly = true)
    public Optional<String> correct(OcrTerm.Kind kind, String ocrText) {
        String norm = normalize(ocrText);
        if (norm.length() < 2) {
            return Optional.empty();
        }
        List<OcrTerm> candidates = repository.findByKind(kind);
        OcrTerm best = null;
        double bestScore = 0;
        for (OcrTerm term : candidates) {
            double score = similarity(norm, term.getNormalized());
            // Tie-break toward the more frequently confirmed term.
            if (score > bestScore || (score == bestScore && best != null
                    && term.getOccurrences() > best.getOccurrences())) {
                bestScore = score;
                best = term;
            }
        }
        if (best != null && bestScore >= MATCH_THRESHOLD) {
            return Optional.of(best.getText());
        }
        return Optional.empty();
    }

    /** Normalized similarity in [0,1] from Levenshtein edit distance. */
    static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        int distance = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return maxLen == 0 ? 1.0 : 1.0 - ((double) distance / maxLen);
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
