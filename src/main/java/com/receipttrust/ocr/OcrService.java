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

    public OcrDtos.ReceiptDraft scan(MultipartFile file) {
        if (!properties.isEnabled()) {
            return OcrDtos.ReceiptDraft.empty();
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            // PDFs and non-images are not OCR'd in this MVP; manual entry follows.
            return OcrDtos.ReceiptDraft.empty();
        }

        File dataDir = new File(properties.getDataPath());
        if (!dataDir.isDirectory()) {
            log.warn("OCR tessdata directory not found at {}; returning empty draft",
                    dataDir.getAbsolutePath());
            return OcrDtos.ReceiptDraft.empty();
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (image == null) {
                return OcrDtos.ReceiptDraft.empty();
            }
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(dataDir.getAbsolutePath());
            tesseract.setLanguage(properties.getLanguage());
            tesseract.setPageSegMode(6); // assume a uniform block of text
            String text = tesseract.doOCR(image);
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
}
