package com.receipttrust.ocr;

import com.receipttrust.config.OcrProperties;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Local, free OCR using the native Tesseract engine via Tess4J. Preprocesses the
 * image (upscale, grayscale, binarize) which helps phone photos of receipts.
 */
@Component
public class TesseractOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);

    /** Common tessdata locations, tried when the configured path is missing. */
    private static final String[] FALLBACK_TESSDATA_DIRS = {
            "/usr/share/tesseract-ocr/5/tessdata",
            "/usr/share/tesseract-ocr/4.00/tessdata",
            "/usr/share/tesseract-ocr/tessdata",
            "/usr/share/tessdata",
            "/usr/local/share/tessdata"
    };

    private final OcrProperties properties;

    public TesseractOcrEngine(OcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return "tesseract";
    }

    @Override
    public boolean isAvailable() {
        return resolveTessdataDir() != null;
    }

    @Override
    public String extractText(byte[] imageBytes) {
        File dataDir = resolveTessdataDir();
        if (dataDir == null) {
            log.warn("OCR tessdata directory not found (configured '{}' and no known fallback contained "
                    + "'{}.traineddata')", properties.getDataPath(), properties.getLanguage());
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                log.warn("Tesseract could not decode uploaded image");
                return null;
            }
            image = preprocess(image);
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(dataDir.getAbsolutePath());
            tesseract.setLanguage(properties.getLanguage());
            tesseract.setPageSegMode(4); // a single column of text of variable sizes (receipts)
            tesseract.setVariable("preserve_interword_spaces", "1");
            String text = tesseract.doOCR(image);
            log.info("Tesseract read {} chars using tessdata at {}",
                    text == null ? 0 : text.length(), dataDir.getAbsolutePath());
            return text;
        } catch (IOException e) {
            log.warn("Failed to read image for OCR: {}", e.getMessage());
            return null;
        } catch (TesseractException e) {
            log.warn("Tesseract OCR failed: {}", e.getMessage());
            return null;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("Tesseract native library unavailable: {}", e.getMessage());
            return null;
        }
    }

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
        return configured.isDirectory() ? configured : null;
    }

    private boolean hasTraineddata(File dir, String lang) {
        return dir.isDirectory() && new File(dir, lang + ".traineddata").isFile();
    }

    /** Upscale small images, convert to grayscale, then binarize to crisp B&W. */
    private BufferedImage preprocess(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) {
            return src;
        }
        double targetLongEdge = 1600.0;
        double longEdge = Math.max(w, h);
        double scale = longEdge < targetLongEdge ? Math.min(3.0, targetLongEdge / longEdge) : 1.0;
        int nw = (int) Math.round(w * scale);
        int nh = (int) Math.round(h * scale);

        BufferedImage gray = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src.getScaledInstance(nw, nh, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();

        return binarize(gray);
    }

    private BufferedImage binarize(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
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
}
