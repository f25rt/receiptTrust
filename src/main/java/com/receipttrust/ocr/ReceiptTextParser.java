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

    // A money amount with a 2-decimal fraction, supporting BOTH conventions:
    //   dot-decimal:   308.00  1,234.50  $6.50
    //   comma-decimal: 308,00  1.234,50  (common from some OCR engines/locales)
    // Group 1 = the whole numeric token incl. separators; the decimal part is the
    // last 2 digits after the final separator (resolved in parseMoney()).
    private static final Pattern MONEY = Pattern.compile("\\$?\\s*(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2}|\\d+[.,]\\d{2})");
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

    // Generic document headers that are NOT the store name (common at the top).
    private static final List<String> GENERIC_HEADERS = List.of(
            "bill", "receipt", "invoice", "official receipt", "sales invoice",
            "order", "order slip", "tax invoice", "customer copy");
    // "10 item(s)" summary line (the store name often precedes it on the same line).
    private static final Pattern ITEM_COUNT = Pattern.compile("\\d+\\s*item", Pattern.CASE_INSENSITIVE);

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
        // Preferred: a summary line like "SUNBURST  10 item(s)  7:05 PM" carries
        // the store name as the leading text before the item count. This is the
        // most reliable signal on receipts whose top is a generic "BILL" header.
        for (String line : lines) {
            Matcher m = ITEM_COUNT.matcher(line);
            if (m.find()) {
                String lead = line.substring(0, m.start()).strip();
                if (isPlausibleStoreName(lead)) {
                    return lead;
                }
            }
        }
        // Otherwise: a clean header line near the top — real text, no amounts, not
        // a generic header or a known non-store keyword, and not an item line
        // (an item line has a trailing amount, which the amount check rejects).
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (isPlausibleStoreName(line)
                    && extractAmounts(line).isEmpty()
                    && !containsAny(lower, SKIP_KEYS)
                    && !ITEM_COUNT.matcher(line).find()
                    && !lower.matches(".*\\d{3,}.*")) {
                return line;
            }
        }
        // Not confident -> leave empty for the user to fill in (per product rule).
        return null;
    }

    /** A plausible store name: enough letters and not a generic document header. */
    private static boolean isPlausibleStoreName(String s) {
        return s != null
                && s.chars().filter(Character::isLetter).count() >= 3
                && !isGenericHeader(s);
    }

    /** True when the line is just a document header (not a store name). */
    private static boolean isGenericHeader(String line) {
        // Normalize OCR spacing like "B I L L" -> "bill".
        String norm = line.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return GENERIC_HEADERS.stream().anyMatch(h -> norm.equals(h.replaceAll("[^a-z]", "")));
    }

    private static OcrDtos.ParsedItem toItem(String line, List<BigDecimal> amounts) {
        // Name is whatever precedes the FIRST money amount on the line.
        Matcher m = MONEY.matcher(line);
        String namePart = m.find() ? line.substring(0, m.start()).strip() : line.strip();

        // Leading quantity column (e.g. "2  Iced Latte" or "2x Latte").
        int quantity = 1;
        boolean explicitQty = false;
        Matcher qm = QTY_PREFIX.matcher(namePart);
        if (qm.find()) {
            try {
                quantity = Integer.parseInt(qm.group(1));
                explicitQty = true;
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

        // Determine unit price and quantity. The first amount is the unit price,
        // the last (when there are two columns) is the line total.
        //
        // When the row has an EXPLICIT leading quantity (e.g. "3 Double - Thigh"),
        // trust it: OCR frequently misreads the line-total digits (e.g. 924 -> 724),
        // which would otherwise derive a wrong quantity (724/308 -> 2). The printed
        // Qty column is the reliable source. Only when there's no leading quantity
        // do we derive it from amount / price.
        BigDecimal unitPrice;
        if (amounts.size() >= 2) {
            // Two columns: first = unit price, last = line total.
            unitPrice = amounts.get(0);
            BigDecimal lineTotal = amounts.get(amounts.size() - 1);
            if (!explicitQty && unitPrice.signum() > 0) {
                // No printed quantity: derive it from total / unit price.
                int derived = lineTotal.divide(unitPrice, 0, RoundingMode.HALF_UP).intValue();
                if (derived >= 1 && derived <= 999) {
                    quantity = derived;
                }
            }
            // With an explicit quantity, trust it (OCR often misreads the line total).
        } else {
            // Single amount. With an explicit quantity > 1 it's the line total, so
            // derive the unit price; otherwise the amount is the unit price (qty 1).
            BigDecimal amount = amounts.get(0);
            if (explicitQty && quantity > 1) {
                unitPrice = amount.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
            } else {
                unitPrice = amount;
            }
        }
        return new OcrDtos.ParsedItem(namePart, quantity, unitPrice);
    }

    /** All money amounts on a line, in order. */
    private static List<BigDecimal> extractAmounts(String line) {
        List<BigDecimal> out = new ArrayList<>();
        Matcher m = MONEY.matcher(line);
        while (m.find()) {
            BigDecimal v = parseMoney(m.group(1));
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * Parses a money token that may use either decimal convention. The final
     * '.' or ',' is treated as the decimal separator; all earlier separators are
     * thousands groupings and removed. E.g. "308,00" -> 308.00, "1.234,50" ->
     * 1234.50, "1,234.50" -> 1234.50.
     */
    private static BigDecimal parseMoney(String token) {
        String t = token.strip().replaceAll("^\\$\\s*", "").replaceAll("\\s+", "");
        int lastDot = t.lastIndexOf('.');
        int lastComma = t.lastIndexOf(',');
        int decimalPos = Math.max(lastDot, lastComma);
        if (decimalPos < 0) {
            try {
                return new BigDecimal(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        String intPart = t.substring(0, decimalPos).replaceAll("[.,]", "");
        String fracPart = t.substring(decimalPos + 1);
        try {
            return new BigDecimal(intPart + "." + fracPart);
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
