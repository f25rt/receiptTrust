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
 * store name, line items (qty/name/price), service charge, and total.
 *
 * Handles two common item layouts:
 *   "2x Oat Milk Latte        13.00"          (qty glued to name, single amount)
 *   " 2  Iced Latte    165.00   330.00"       (qty column, Price + Amount columns)
 *
 * Dependency-free and pure so it can be unit tested without the native engine.
 */
public final class ReceiptTextParser {

    // One money amount anywhere: 12.00 / 1,234.50 / $6.50 / 1.268,96 not handled (dot-decimal only).
    private static final Pattern MONEY = Pattern.compile("\\$?\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)\\.(\\d{2})");
    // Leading quantity column: "2x", "2 x", or just "2 " at the very start.
    private static final Pattern QTY_PREFIX = Pattern.compile("^\\s*(\\d{1,3})\\s*[xX]?\\s+");

    private static final List<String> TOTAL_KEYS = List.of("total", "amount due", "balance due", "grand total");
    private static final List<String> SERVICE_KEYS = List.of("service charge", "service", "svc charge", "gratuity", "tip");
    private static final List<String> SKIP_KEYS = List.of(
            "subtotal", "sub total", "tax", "vat", "change", "cash", "card", "visa",
            "mastercard", "balance", "tender", "amount tendered", "auth", "approval",
            "thank", "receipt", "invoice", "order", "table", "server", "cashier",
            "date", "time", "tel", "phone", "www", "http", "discount", "payment",
            "qty", "item description", "price", "amount");

    private ReceiptTextParser() {
    }

    public static OcrDtos.ReceiptDraft parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return OcrDtos.ReceiptDraft.empty();
        }

        List<String> lines = new ArrayList<>();
        for (String l : rawText.split("\\r?\\n")) {
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
            List<BigDecimal> amounts = extractAmounts(line);
            if (amounts.isEmpty()) {
                continue;
            }
            BigDecimal last = amounts.get(amounts.size() - 1);

            if (containsAny(lower, SERVICE_KEYS)) {
                serviceCharge = last;
                continue;
            }
            if (containsAny(lower, TOTAL_KEYS)) {
                if (total == null || last.compareTo(total) > 0) {
                    total = last;
                }
                continue;
            }
            if (containsAny(lower, SKIP_KEYS)) {
                continue;
            }

            OcrDtos.ParsedItem item = toItem(line, amounts);
            if (item != null) {
                items.add(item);
            }
        }

        return new OcrDtos.ReceiptDraft(storeName, items, serviceCharge, total, rawText);
    }

    private static String detectStoreName(List<String> lines) {
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            long letters = line.chars().filter(Character::isLetter).count();
            if (letters >= 3
                    && extractAmounts(line).isEmpty()
                    && !containsAny(lower, SKIP_KEYS)
                    && !lower.matches(".*\\d{3,}.*")) {
                return line;
            }
        }
        return lines.isEmpty() ? null : lines.get(0);
    }

    private static OcrDtos.ParsedItem toItem(String line, List<BigDecimal> amounts) {
        // Name is whatever precedes the FIRST money amount on the line.
        Matcher m = MONEY.matcher(line);
        String namePart = m.find() ? line.substring(0, m.start()).strip() : line.strip();

        // Leading quantity column (e.g. "2  Iced Latte" or "2x Latte").
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

        // Strip leading non-letter noise and trailing punctuation.
        namePart = namePart.replaceAll("^[^A-Za-z0-9]+", "").replaceAll("[.:_\\-]+$", "").strip();

        // The Qty column is frequently misread by OCR into a short junk token
        // (e.g. "1" -> "al", "il", "a") stuck before the real item name. When the
        // line has a leading short token (<=2 chars) followed by a capitalized
        // word, drop that token.
        Matcher junk = Pattern.compile("^[A-Za-z]{1,2}\\s+([A-Z].*)$").matcher(namePart);
        if (junk.matches()) {
            namePart = junk.group(1).strip();
        }

        if (namePart.length() < 2 || namePart.chars().noneMatch(Character::isLetter)) {
            return null;
        }

        // Determine unit price and quantity:
        //  - Two amounts (Price + Amount columns): first = unit price, last = line total.
        //    Derive quantity from amount/price (more reliable than the OCR'd Qty digit).
        //  - One amount: it's the line total; divide by the parsed quantity.
        BigDecimal unitPrice;
        if (amounts.size() >= 2) {
            unitPrice = amounts.get(0);
            BigDecimal lineTotal = amounts.get(amounts.size() - 1);
            if (unitPrice.signum() > 0) {
                int derived = lineTotal.divide(unitPrice, 0, RoundingMode.HALF_UP).intValue();
                if (derived >= 1 && derived <= 999) {
                    quantity = derived;
                }
            }
        } else {
            BigDecimal lineTotal = amounts.get(0);
            unitPrice = quantity > 1
                    ? lineTotal.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP)
                    : lineTotal;
        }
        return new OcrDtos.ParsedItem(namePart, quantity, unitPrice);
    }

    /** All money amounts on a line, in order. */
    private static List<BigDecimal> extractAmounts(String line) {
        List<BigDecimal> out = new ArrayList<>();
        Matcher m = MONEY.matcher(line);
        while (m.find()) {
            String whole = m.group(1).replace(",", "");
            try {
                out.add(new BigDecimal(whole + "." + m.group(2)));
            } catch (NumberFormatException ignored) {
                // skip malformed
            }
        }
        return out;
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
