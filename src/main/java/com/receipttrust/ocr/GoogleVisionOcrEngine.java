package com.receipttrust.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.receipttrust.config.OcrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Cloud OCR via Google Cloud Vision (DOCUMENT_TEXT_DETECTION) over the REST API
 * using an API key. Far more accurate than Tesseract on messy phone photos.
 * Inert unless {@code receipttrust.ocr.vision-api-key} is set.
 *
 * Pricing note: first 1,000 images/month are free, then ~$1.50 per 1,000.
 */
@Component
public class GoogleVisionOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(GoogleVisionOcrEngine.class);
    private static final String ENDPOINT = "https://vision.googleapis.com/v1/images:annotate";

    private final OcrProperties properties;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public GoogleVisionOcrEngine(OcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return "google";
    }

    @Override
    public boolean isAvailable() {
        String key = properties.getVisionApiKey();
        return key != null && !key.isBlank();
    }

    @Override
    public String extractText(byte[] imageBytes) {
        if (!isAvailable()) {
            log.warn("Google Vision selected but GOOGLE_VISION_API_KEY is not set");
            return null;
        }
        try {
            String body = buildRequestBody(imageBytes);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT + "?key=" + properties.getVisionApiKey()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Google Vision returned HTTP {}: {}", response.statusCode(),
                        truncate(response.body(), 300));
                return null;
            }
            return parseText(response.body());
        } catch (Exception e) {
            log.warn("Google Vision OCR call failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildRequestBody(byte[] imageBytes) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode requests = root.putArray("requests");
        ObjectNode req = requests.addObject();
        req.putObject("image").put("content", Base64.getEncoder().encodeToString(imageBytes));
        ObjectNode feature = req.putArray("features").addObject();
        feature.put("type", "DOCUMENT_TEXT_DETECTION");
        return root.toString();
    }

    private String parseText(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode responses = root.path("responses");
        if (responses.isArray() && !responses.isEmpty()) {
            JsonNode first = responses.get(0);
            // Preferred: fullTextAnnotation.text; fallback: first textAnnotations entry.
            JsonNode full = first.path("fullTextAnnotation").path("text");
            if (full.isTextual() && !full.asText().isBlank()) {
                return full.asText();
            }
            JsonNode annotations = first.path("textAnnotations");
            if (annotations.isArray() && !annotations.isEmpty()) {
                return annotations.get(0).path("description").asText(null);
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
