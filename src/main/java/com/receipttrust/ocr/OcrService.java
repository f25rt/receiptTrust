package com.receipttrust.ocr;

import com.receipttrust.config.OcrProperties;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
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

    public OcrService(OcrProperties properties) {
        this.properties = properties;
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
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(dataDir.getAbsolutePath());
            tesseract.setLanguage(properties.getLanguage());
            tesseract.setPageSegMode(6); // assume a uniform block of text
            String text = tesseract.doOCR(image);
            log.info("OCR read {} chars using tessdata at {}",
                    text == null ? 0 : text.length(), dataDir.getAbsolutePath());
            return ReceiptTextParser.parse(text);
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
}
