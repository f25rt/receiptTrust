package com.receipttrust.ocr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw OCR text from a receipt into a best-effort structured draft:
 * store name, line items (with optional quantity), service charge, and total.
 *
 * This is deliberately dependency-free and pure so it can be unit tested without
 * the native Tesseract engine.
 */
public final class ReceiptTextParser {

    // A trailing money amount, e.g. "12.00", "1,234.50", "$6.50".
    private static final Pattern PRICE = Pattern.compile("\\$?\\s*(\\d{1,3}(?:[.,]\\d{3})*|\\d+)[.,](\\d{2})\\s*$");
    // Optional leading quantity: "2x", "2 x", "2 ".
    private static final Pattern QTY_PREFIX = Pattern.compile("^\\s*(\\d{1,3})\\s*[xX]?\\s+");

    private static final List<String> TOTAL_KEYS = List.of("total", "amount due", "balance due", "grand total");
    private static final List<String> SERVICE_KEYS = List.of("service charge", "service", "svc charge", "gratuity", "tip");
    private static final List<String> SKIP_KEYS = List.of(
            "subtotal", "sub total", "tax", "vat", "change", "cash", "card", "visa",
            "mastercard", "balance", "tender", "auth", "approval", "thank", "receipt",
            "invoice", "order", "table", "server", "cashier", "date", "time", "tel",
            "phone", "www", "http", "discount");

    private ReceiptTextParser() {
    }

    public static OcrDtos.ReceiptDraft parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return OcrDtos.ReceiptDraft.empty();
        }

        String[] rawLines = rawText.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            String t = l.strip();
            if (!t.isEmpty()) {
                lines.add(t);
            }
        }

        String storeName = detectStoreName(lines);
        BigDecimal serviceCharge = null;
        BigDecimal total = null;
        List<OcrDtos.ParsedItem> items = new ArrayList<>();

        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            BigDecimal price = extractTrailingPrice(line);

            if (price == null) {
                continue;
            }

            if (containsAny(lower, SERVICE_KEYS)) {
                serviceCharge = price;
                continue;
            }
            if (containsAny(lower, TOTAL_KEYS)) {
                // Keep the largest total-looking value (grand total beats subtotal).
                if (total == null || price.compareTo(total) > 0) {
                    total = price;
                }
                continue;
            }
            if (containsAny(lower, SKIP_KEYS)) {
                continue;
            }

            // Treat as a line item: strip the trailing price, parse optional quantity.
            OcrDtos.ParsedItem item = toItem(line, price);
            if (item != null) {
                items.add(item);
            }
        }

        return new OcrDtos.ReceiptDraft(storeName, items, serviceCharge, total, rawText);
    }

    private static String detectStoreName(List<String> lines) {
        // First line that is mostly letters and not a number/price/address marker.
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            long letters = line.chars().filter(Character::isLetter).count();
            if (letters >= 3
                    && extractTrailingPrice(line) == null
                    && !containsAny(lower, SKIP_KEYS)
                    && !lower.matches(".*\\d{3,}.*")) {
                return line;
            }
        }
        return lines.isEmpty() ? null : lines.get(0);
    }

    private static OcrDtos.ParsedItem toItem(String line, BigDecimal price) {
        // Remove the trailing price text.
        Matcher pm = PRICE.matcher(line);
        String namePart = pm.find() ? line.substring(0, pm.start()).strip() : line.strip();

        int quantity = 1;
        Matcher qm = QTY_PREFIX.matcher(namePart);
        if (qm.find()) {
            try {
                quantity = Integer.parseInt(qm.group(1));
                namePart = namePart.substring(qm.end()).strip();
            } catch (NumberFormatException ignored) {
                quantity = 1;
            }
        }

        namePart = namePart.replaceAll("[.:_\\-]+$", "").strip();
        if (namePart.length() < 2) {
            return null;
        }
        // Unit price = line price / quantity (line price is the extended amount).
        BigDecimal unit = quantity > 1
                ? price.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP)
                : price;
        return new OcrDtos.ParsedItem(namePart, quantity, unit);
    }

    private static BigDecimal extractTrailingPrice(String line) {
        Matcher m = PRICE.matcher(line);
        if (!m.find()) {
            return null;
        }
        String whole = m.group(1).replace(",", "").replace(".", "");
        String cents = m.group(2);
        try {
            return new BigDecimal(whole + "." + cents);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
