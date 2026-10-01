package com.receipttrust.ocr;

import com.receipttrust.config.OcrProperties;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Runs Tesseract OCR over an uploaded receipt image and returns a parsed draft.
 * Degrades gracefully: if OCR is disabled, tessdata is missing, or the file is a
 * PDF/unsupported image, it returns an empty draft so the user can still enter
 * details manually.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final OcrProperties properties;
    private final OcrVocabularyService vocabulary;

    public OcrService(OcrProperties properties, OcrVocabularyService vocabulary) {
        this.properties = properties;
        this.vocabulary = vocabulary;
    }

    /** Common tessdata locations, tried when the configured path is missing. */
    private static final String[] FALLBACK_TESSDATA_DIRS = {
            "/usr/share/tesseract-ocr/5/tessdata",
            "/usr/share/tesseract-ocr/4.00/tessdata",
            "/usr/share/tesseract-ocr/tessdata",
            "/usr/share/tessdata",
            "/usr/local/share/tessdata"
    };

    public OcrDtos.ReceiptDraft scan(MultipartFile file) {
        if (!properties.isEnabled()) {
            return OcrDtos.ReceiptDraft.empty();
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            // PDFs and non-images are not OCR'd in this MVP; manual entry follows.
            return OcrDtos.ReceiptDraft.empty();
        }

        File dataDir = resolveTessdataDir();
        if (dataDir == null) {
            log.warn("OCR tessdata directory not found (configured '{}' and no known fallback contained "
                            + "'{}.traineddata'); returning empty draft",
                    properties.getDataPath(), properties.getLanguage());
            return OcrDtos.ReceiptDraft.empty();
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (image == null) {
                log.warn("OCR could not decode uploaded image (content-type {})", contentType);
                return OcrDtos.ReceiptDraft.empty();
            }
            image = preprocess(image);
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(dataDir.getAbsolutePath());
            tesseract.setLanguage(properties.getLanguage());
            tesseract.setPageSegMode(4); // a single column of text of variable sizes (receipts)
            tesseract.setVariable("preserve_interword_spaces", "1");
            String text = tesseract.doOCR(image);
            log.info("OCR read {} chars using tessdata at {}",
                    text == null ? 0 : text.length(), dataDir.getAbsolutePath());
            // Log a snippet of the raw text so OCR quality is visible in server logs.
            if (log.isInfoEnabled() && text != null) {
                String snippet = text.replaceAll("\\s+", " ").strip();
                log.info("OCR raw text (first 300 chars): {}",
                        snippet.length() > 300 ? snippet.substring(0, 300) : snippet);
            }
            if (looksLikeGibberish(text)) {
                log.info("OCR output looks unreliable; returning empty draft for manual entry");
                return OcrDtos.ReceiptDraft.empty();
            }
            return applyVocabulary(ReceiptTextParser.parse(text));
        } catch (IOException e) {
            log.warn("Failed to read image for OCR: {}", e.getMessage());
            return OcrDtos.ReceiptDraft.empty();
        } catch (TesseractException e) {
            log.warn("Tesseract OCR failed: {}", e.getMessage());
            return OcrDtos.ReceiptDraft.empty();
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            // Native Tesseract library unavailable on this host.
            log.warn("Tesseract native library unavailable: {}", e.getMessage());
            return OcrDtos.ReceiptDraft.empty();
        }
    }

    /**
     * Returns a directory containing {@code <lang>.traineddata}: the configured
     * path if valid, otherwise the first known system location that has it.
     */
    private File resolveTessdataDir() {
        String lang = properties.getLanguage();
        File configured = new File(properties.getDataPath());
        if (hasTraineddata(configured, lang)) {
            return configured;
        }
        for (String candidate : FALLBACK_TESSDATA_DIRS) {
            File dir = new File(candidate);
            if (hasTraineddata(dir, lang)) {
                return dir;
            }
        }
        // Last resort: if the configured dir exists at all, use it (Tess4J may still find data).
        return configured.isDirectory() ? configured : null;
    }

    private boolean hasTraineddata(File dir, String lang) {
        return dir.isDirectory() && new File(dir, lang + ".traineddata").isFile();
    }

    /**
     * Snaps the parsed store name and item names to known confirmed terms when a
     * close match exists in the global vocabulary. This is how recognition
     * improves over time without retraining Tesseract.
     */
    private OcrDtos.ReceiptDraft applyVocabulary(OcrDtos.ReceiptDraft draft) {
        String store = draft.storeName();
        if (store != null) {
            store = vocabulary.correct(OcrTerm.Kind.STORE, store).orElse(store);
        }
        java.util.List<OcrDtos.ParsedItem> items = new java.util.ArrayList<>();
        for (OcrDtos.ParsedItem item : draft.items()) {
            String name = vocabulary.correct(OcrTerm.Kind.ITEM, item.name()).orElse(item.name());
            items.add(new OcrDtos.ParsedItem(name, item.quantity(), item.unitPrice()));
        }
        return new OcrDtos.ReceiptDraft(store, items, draft.serviceCharge(), draft.total(), draft.rawText());
    }

    /**
     * Light preprocessing to improve OCR on phone photos: upscale small images so
     * text is tall enough for Tesseract, and convert to grayscale (reduces color
     * noise from the receipt/background). Kept intentionally simple and
     * dependency-free; heavier deskew/binarization would need OpenCV.
     */
    private BufferedImage preprocess(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) {
            return src;
        }
        // Tesseract likes ~1500-2000px on the long edge; upscale smaller images.
        double targetLongEdge = 1600.0;
        double longEdge = Math.max(w, h);
        double scale = longEdge < targetLongEdge ? Math.min(3.0, targetLongEdge / longEdge) : 1.0;
        int nw = (int) Math.round(w * scale);
        int nh = (int) Math.round(h * scale);

        BufferedImage gray = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src.getScaledInstance(nw, nh, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();

        return binarize(gray);
    }

    /**
     * Converts a grayscale image to black & white using a global threshold at the
     * mean luminance. Dot-matrix / thermal receipts OCR far better as crisp B&W
     * than as a noisy grayscale photo.
     */
    private BufferedImage binarize(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();

        // Compute mean luminance as the threshold.
        long sum = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                sum += gray.getRaster().getSample(x, y, 0);
            }
        }
        int threshold = (int) (sum / ((long) w * h));

        BufferedImage bw = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = gray.getRaster().getSample(x, y, 0);
                bw.getRaster().setSample(x, y, 0, v < threshold ? 0 : 255);
            }
        }
        return bw;
    }

    /**
     * Heuristic to detect unusable OCR output (random letters/noise) so we can
     * fall back to clean manual entry instead of showing the user gibberish.
     * Flags text with very few real words (letter-runs of length >= 3).
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
        // If there's barely any multi-letter content, or almost no 3+ letter words
        // among the alphabetic tokens, treat it as unreliable.
        if (realWords < 2) {
            return true;
        }
        return alphaTokens > 0 && ((double) realWords / alphaTokens) < 0.25;
    }
}
