package com.receipttrust.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.receipttrust.config.OcrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Cloud OCR via OCR.space (https://ocr.space). Free tier needs no credit card
 * (~25,000 requests/month). Returns raw text that feeds {@link ReceiptTextParser}.
 * Inert unless {@code receipttrust.ocr.ocrspace-api-key} is set.
 */
@Component
public class OcrSpaceOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(OcrSpaceOcrEngine.class);
    private static final String ENDPOINT = "https://api.ocr.space/parse/image";

    private final OcrProperties properties;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public OcrSpaceOcrEngine(OcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return "ocrspace";
    }

    @Override
    public boolean isAvailable() {
        String key = properties.getOcrspaceApiKey();
        return key != null && !key.isBlank();
    }

    @Override
    public String extractText(byte[] imageBytes) {
        if (!isAvailable()) {
            log.warn("OCR.space selected but OCRSPACE_API_KEY is not set");
            return null;
        }
        try {
            // Keep the payload under OCR.space's free-tier 1MB limit by re-encoding
            // as JPEG (and downscaling if needed). Falls back to the original bytes.
            byte[] jpeg = toBoundedJpeg(imageBytes, 950_000);
            String base64 = Base64.getEncoder().encodeToString(jpeg);
            String form = "base64Image=" + enc("data:image/jpeg;base64," + base64)
                    + "&language=" + enc(mapLanguage(properties.getLanguage()))
                    + "&OCREngine=2"      // engine 2: better on photos/receipts
                    + "&scale=true"       // upscale low-res images
                    + "&isTable=true";    // preserve column/row layout (receipts)

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(45))
                    .header("apikey", properties.getOcrspaceApiKey())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("OCR.space returned HTTP {}: {}", response.statusCode(),
                        truncate(response.body(), 300));
                return null;
            }
            return parseText(response.body());
        } catch (Exception e) {
            log.warn("OCR.space call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Re-encodes the image as JPEG, downscaling and lowering quality until it
     * fits under {@code maxBytes} (OCR.space free tier caps the base64 endpoint
     * at ~1MB). Returns the original bytes if it cannot be decoded.
     */
    private byte[] toBoundedJpeg(byte[] original, int maxBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
            if (img == null) {
                return original;
            }
            // Cap the long edge to keep text legible but the file small.
            int longEdge = Math.max(img.getWidth(), img.getHeight());
            int maxLongEdge = 2000;
            if (longEdge > maxLongEdge) {
                double scale = (double) maxLongEdge / longEdge;
                int nw = (int) Math.round(img.getWidth() * scale);
                int nh = (int) Math.round(img.getHeight() * scale);
                BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = resized.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH), 0, 0, null);
                g.dispose();
                img = resized;
            }
            byte[] out = encodeJpeg(img, 0.85f);
            // If still too big, step quality down (best effort).
            float q = 0.7f;
            while (out.length > maxBytes && q >= 0.4f) {
                out = encodeJpeg(img, q);
                q -= 0.15f;
            }
            return out;
        } catch (Exception e) {
            log.warn("Could not re-encode image for OCR.space, sending original: {}", e.getMessage());
            return original;
        }
    }

    private byte[] encodeJpeg(BufferedImage img, float quality) throws java.io.IOException {
        var writers = ImageIO.getImageWritersByFormatName("jpeg");
        javax.imageio.ImageWriter writer = writers.next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private String parseText(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        if (root.path("IsErroredOnProcessing").asBoolean(false)) {
            JsonNode msg = root.path("ErrorMessage");
            log.warn("OCR.space processing error: {}", msg.toString());
            return null;
        }
        JsonNode results = root.path("ParsedResults");
        if (results.isArray() && !results.isEmpty()) {
            String text = results.get(0).path("ParsedText").asText(null);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /** OCR.space uses 3-letter language codes for engine 1; "eng" works for engine 2. */
    private String mapLanguage(String lang) {
        return (lang == null || lang.isBlank()) ? "eng" : lang;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
