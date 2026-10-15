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
    // Allows ':' as a grouping separator too, since OCR sometimes reads a comma
    // thousands-separator as a colon (e.g. "2:155,65" -> 2155.65).
    private static final Pattern MONEY = Pattern.compile("\\$?\\s*(\\d{1,3}(?:[.,:]\\d{3})*[.,]\\d{2}|\\d+[.,]\\d{2})");
    // A bare integer amount (no cents), e.g. "220". Bounded by word edges so it
    // isn't a fragment of a longer number. Used only for receipts that print
    // whole-number prices (common for peso receipts).
    private static final Pattern INT_AMOUNT = Pattern.compile("(?<![\\d.,-])(\\d{1,6})(?![\\d.,:/-])");
    // Leading quantity column: "2x", "2 x", or just "2 " at the very start.
    private static final Pattern QTY_PREFIX = Pattern.compile("^\\s*(\\d{1,3})\\s*[xX]?\\s+");

    // Lines that are identifiers/metadata (TIN, invoice no., MIN, serial, dates,
    // phone, bill no.) whose digits must never be read as money amounts.
    private static final Pattern IDENTIFIER_LINE = Pattern.compile(
            "(tin|min|s/n|s\\.n|inv\\b|invoice|reg\\b|no\\.|number|serial|bill|#|\\d{1,2}[:/]\\d{2})",
            Pattern.CASE_INSENSITIVE);
    // A long unbroken digit run (invoice/serial) — never an amount.
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\d{7,}");
    // A 4-digit year (date lines like "Sep 07, 2026" have no decimal amounts and
    // must not fall through to the integer-amount reader).
    private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern MONTH_NAME = Pattern.compile(
            "\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\b", Pattern.CASE_INSENSITIVE);
    // Invoice / receipt / OR number, e.g. "Inv. No.:0000000000000863715",
    // "Invoice #: 12345", "OR No. 00897". Captures the alphanumeric id after the label.
    private static final Pattern INVOICE_NO = Pattern.compile(
            "(?:invoice|inv|receipt|or)\\.?\\s*(?:no\\.?|number|#)?\\s*[:.#]?\\s*([A-Za-z0-9-]*\\d[A-Za-z0-9-]*)",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> TOTAL_KEYS = List.of("total", "amount due", "balance due", "grand total");
    private static final List<String> SERVICE_KEYS = List.of("service charge", "service", "svc charge", "gratuity", "tip");
    // Explicit VAT/tax AMOUNT lines. "vat sales"/"vatable" are handled separately
    // as the base, so they must not be matched here.
    private static final List<String> TAX_KEYS = List.of("vat amount", "vat (12", "12% vat", "gst", "sales tax", "tax");
    // The net base that VAT is charged on.
    private static final List<String> VATABLE_KEYS = List.of("vatable", "vat-able", "vat able", "vat sales", "vatable sales");
    private static final List<String> SUBTOTAL_KEYS = List.of("subtotal", "sub total", "sub-total");
    /** Philippine VAT rate (12%), used to compute VAT from the VATable base. */
    private static final BigDecimal VAT_RATE = new BigDecimal("0.12");
    private static final BigDecimal VAT_DIVISOR = new BigDecimal("1.12");
    // Signals a VAT receipt (so we only derive 12% VAT when VAT is actually declared).
    private static final Pattern VAT_MARKER = Pattern.compile("\\b(vat|tin)\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> SKIP_KEYS = List.of(
            "subtotal", "sub total", "tax", "vat", "change", "cash", "card", "visa",
            "mastercard", "balance", "tender", "amount tendered", "auth", "approval",
            "thank", "receipt", "invoice", "order", "table", "server", "cashier",
            "date", "time", "tel", "phone", "www", "http", "discount", "payment",
            "qty", "item description", "price", "amount",
            // Payment/summary and store-metadata lines that must not be items.
            "credit", "debit", "gcash", "maya", "e-wallet", "ref no", "permit",
            "pos s/n", "s/n", "min:", "ptu", "service invoice", "reprint",
            "vat sales", "vatable", "vat-exempt", "zero-rated", "vat exempt");

    // Address/location markers: a line with these is a store address, not an item.
    private static final Pattern ADDRESS_LINE = Pattern.compile(
            "\\b(unit|u-l|bldg|building|floor|f\\b|st\\.|street|ave|avenue|blvd|road|rd\\.|"
                    + "brgy|barrio|city|park|center|centre|cor\\b|corner|phase|block|lot)\\b",
            Pattern.CASE_INSENSITIVE);

    // Business-entity suffixes that strongly indicate a store/company name line.
    private static final Pattern BUSINESS_SUFFIX = Pattern.compile(
            "\\b(corp|corporation|inc|incorporated|llc|ltd|co|company|enterprises?|"
                    + "restaurant|cafe|coffee|store|mart|shop|foods?|group)\\b\\.?",
            Pattern.CASE_INSENSITIVE);

    // Generic document headers that are NOT the store name (common at the top).
    private static final List<String> GENERIC_HEADERS = List.of(
            "bill", "receipt", "invoice", "official receipt", "sales invoice",
            "order", "order slip", "tax invoice", "customer copy");
    // "10 item(s)" summary line (the store name often precedes it on the same line).
    private static final Pattern ITEM_COUNT = Pattern.compile("\\d+\\s*item", Pattern.CASE_INSENSITIVE);
    // Order-multiplier sub-line printed under an item, e.g. "3 ORD x @80.00" or
    // "2 x @ 150.00" — it means the item above is qty N at unit price P. Captured
    // so it refines the previous item instead of becoming a junk item of its own.
    // Matches lines like "3 ORD x @80.00", "3 DRD x 080,00" (OCR misreads ORD->DRD
    // and @->0), "2 x 150.00", "2 pcs @ 75". The leading count, an optional
    // ord/pc/x token, an optional '@' (often OCR'd as a leading 0), then the unit
    // price. The unit price keeps only its last 2 digits as decimals in parseMoney.
    private static final Pattern ORDER_MULTIPLIER = Pattern.compile(
            "^\\s*(\\d{1,3})\\s*(?:ord|ords|drd|orders?|pc|pcs|x)\\b[\\sx@0]*?(\\d{1,4}[.,]\\d{2})\\s*$",
            Pattern.CASE_INSENSITIVE);

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
        String invoiceNumber = detectInvoiceNumber(lines);
        BigDecimal serviceCharge = null;
        BigDecimal tax = null;            // explicit VAT/tax amount line, if printed
        BigDecimal vatableSales = null;   // "VATable / VAT Sales" base, if printed
        BigDecimal subtotal = null;
        BigDecimal total = null;
        List<OcrDtos.ParsedItem> items = new ArrayList<>();

        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);

            // Order-multiplier sub-line ("3 ORD x @80.00"): refine the previous
            // item with its real quantity and unit price instead of creating a
            // bogus item. The item's line total above is kept as the source of truth.
            Matcher mult = ORDER_MULTIPLIER.matcher(line);
            if (mult.matches() && !items.isEmpty()) {
                int qty = Integer.parseInt(mult.group(1));
                BigDecimal unit = parseMoney(mult.group(2));
                if (qty >= 1 && unit != null && unit.signum() > 0) {
                    OcrDtos.ParsedItem prev = items.get(items.size() - 1);
                    items.set(items.size() - 1,
                            new OcrDtos.ParsedItem(prev.name(), qty, unit));
                }
                continue;
            }

            List<BigDecimal> amounts = extractAmounts(line);
            if (amounts.isEmpty()) {
                continue;
            }
            BigDecimal last = amounts.get(amounts.size() - 1);

            if (containsAny(lower, SERVICE_KEYS)) {
                serviceCharge = last;
                continue;
            }
            // "VATable sales" / "VAT Sales" base amount (used to compute 12% VAT).
            // A positive value here is the net on which VAT is charged.
            if (containsAny(lower, VATABLE_KEYS)) {
                if (last.signum() > 0) {
                    vatableSales = last;
                }
                continue;
            }
            // Explicit VAT / tax / GST amount line (e.g. "VAT (12%) 219.96").
            // Checked before TOTAL so "total" lines don't swallow it, and before
            // SKIP so it's captured. Only a positive value overrides.
            if (containsAny(lower, TAX_KEYS)) {
                if (last.signum() > 0) {
                    tax = last;
                }
                continue;
            }
            if (containsAny(lower, SUBTOTAL_KEYS)) {
                subtotal = last;
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
            // Store address lines (which often carry unit/floor numbers that look
            // like prices) are not items.
            if (ADDRESS_LINE.matcher(line).find()) {
                continue;
            }

            OcrDtos.ParsedItem item = toItem(line, amounts);
            if (item != null) {
                items.add(item);
            }
        }

        // Only treat this as a VAT receipt (and derive 12% VAT from a VAT-inclusive
        // subtotal) when the text actually signals VAT — a VAT/TIN marker. This
        // avoids inventing a tax for non-VAT (e.g. USD) receipts.
        boolean vatDocument = VAT_MARKER.matcher(rawText).find();
        tax = resolveVat(tax, vatableSales, subtotal, serviceCharge, total, vatDocument);

        return new OcrDtos.ReceiptDraft(storeName, invoiceNumber, items, serviceCharge, tax, total, rawText);
    }

    /** Extracts the invoice/receipt number from a label line, if present. */
    private static String detectInvoiceNumber(List<String> lines) {
        for (String line : lines) {
            Matcher m = INVOICE_NO.matcher(line);
            if (m.find()) {
                String id = m.group(1).strip();
                // Must contain at least one digit (avoid matching stray words).
                if (id.chars().anyMatch(Character::isDigit)) {
                    return id;
                }
            }
        }
        return null;
    }

    private static String detectStoreName(List<String> lines) {
        // Strongest signal: a segment with a business suffix (CORP/INC/CAFE/...),
        // even when OCR glued it onto another line via a tab (e.g.
        // "VAT REGISTERED TIN: ...\tSACHI BEST RAMEN CORP."). Check tab/2+-space
        // segments of the first several lines.
        for (String line : lines.subList(0, Math.min(lines.size(), 6))) {
            for (String seg : line.split("\\t|\\s{2,}")) {
                String s = seg.strip();
                if (BUSINESS_SUFFIX.matcher(s).find()
                        && isPlausibleStoreName(s)
                        && extractAmounts(s).isEmpty()
                        && !ADDRESS_LINE.matcher(s).find()) {
                    return s;
                }
            }
        }
        // Next: a summary line like "SUNBURST  10 item(s)  7:05 PM" carries the
        // store name as the leading text before the item count.
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
        // a generic header, address, or a known non-store keyword.
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (isPlausibleStoreName(line)
                    && extractAmounts(line).isEmpty()
                    && !containsAny(lower, SKIP_KEYS)
                    && !ADDRESS_LINE.matcher(line).find()
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

    /**
     * Resolves the VAT (12%) amount. Prefers an explicitly printed VAT amount;
     * otherwise computes 12% of the VATable sales; otherwise derives the VAT
     * embedded in the subtotal (VAT-inclusive). Returns null if nothing usable.
     */
    private static BigDecimal resolveVat(BigDecimal explicitTax, BigDecimal vatableSales,
                                         BigDecimal subtotal, BigDecimal serviceCharge,
                                         BigDecimal total, boolean vatDocument) {
        if (explicitTax != null && explicitTax.signum() > 0) {
            return explicitTax.setScale(2, RoundingMode.HALF_UP);
        }
        if (vatableSales != null && vatableSales.signum() > 0) {
            // VAT = 12% of the VATable base.
            return vatableSales.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
        }
        // Derive the VAT baked into a VAT-inclusive subtotal, but only for receipts
        // that actually declare VAT (base = subtotal / 1.12 ; vat = subtotal - base).
        if (!vatDocument) {
            return null;
        }
        BigDecimal inclusive = subtotal;
        if (inclusive == null && total != null) {
            inclusive = serviceCharge != null ? total.subtract(serviceCharge) : total;
        }
        if (inclusive != null && inclusive.signum() > 0) {
            BigDecimal base = inclusive.divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP);
            BigDecimal vat = inclusive.subtract(base).setScale(2, RoundingMode.HALF_UP);
            return vat.signum() > 0 ? vat : null;
        }
        return null;
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

    /**
     * Collapses OCR spaces inside a money token so "2 , 053. 00" becomes
     * "2,053.00". Narrowly targets a digit, then a separator surrounded by
     * optional spaces, then digits — repeated — so ordinary text (dates like
     * "Sep 07, 2026") is left untouched.
     */
    private static String normalizeNumberSpaces(String line) {
        // Collapse spaces that sit next to a separator which is itself between
        // digit groups, e.g. "2 , 053. 00" -> "2,053.00". The separator must have
        // a digit before it (ignoring spaces) AND digits after it (ignoring
        // spaces), so dates like "Sep 07, 2026" (space only AFTER the comma, text
        // before) and plain text are left untouched. Applied repeatedly to catch
        // multiple separators in one number.
        String prev;
        String s = line;
        do {
            prev = s;
            s = s.replaceAll("(\\d)\\s*([.,:])\\s*(\\d)", "$1$2$3");
        } while (!s.equals(prev));
        return s;
    }

    /** All money amounts on a line, in order. */
    private static List<BigDecimal> extractAmounts(String rawLine) {
        // Don't fuse number-spaces on date/identifier lines (e.g. "Sep 07, 2026");
        // their digits are not amounts and fusing them creates false matches.
        String line = isIdentifierLine(rawLine) ? rawLine : normalizeNumberSpaces(rawLine);
        List<BigDecimal> out = new ArrayList<>();
        Matcher m = MONEY.matcher(line);
        while (m.find()) {
            BigDecimal v = parseMoney(m.group(1));
            if (v != null) {
                out.add(v);
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        // Fallback: receipts that print whole-number prices (e.g. "220", "24").
        // Only when the line has no decimal amounts and is not an identifier line
        // (TIN / invoice / serial / date), to avoid reading ID digits as money.
        if (isIdentifierLine(line)) {
            return out;
        }
        Matcher im = INT_AMOUNT.matcher(line);
        while (im.find()) {
            try {
                out.add(new BigDecimal(im.group(1)));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return out;
    }

    /** True when a line holds identifier/metadata digits that are not amounts. */
    private static boolean isIdentifierLine(String line) {
        return LONG_DIGIT_RUN.matcher(line).find()
                || IDENTIFIER_LINE.matcher(line).find()
                || YEAR.matcher(line).find()
                || MONTH_NAME.matcher(line).find();
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
        String intPart = t.substring(0, decimalPos).replaceAll("[.,:]", "");
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
