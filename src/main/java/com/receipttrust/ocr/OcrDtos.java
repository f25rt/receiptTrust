package com.receipttrust.ocr;

import java.math.BigDecimal;
import java.util.List;

public final class OcrDtos {

    private OcrDtos() {
    }

    public record ParsedItem(
            String name,
            int quantity,
            BigDecimal unitPrice
    ) {
    }

    /**
     * Draft data extracted from a receipt image. All fields are best-effort and
     * meant to be reviewed and corrected by the user before creating the receipt.
     */
    public record ReceiptDraft(
            String storeName,
            String invoiceNumber,
            List<ParsedItem> items,
            BigDecimal serviceCharge,
            BigDecimal tax,
            BigDecimal total,
            String rawText
    ) {
        public static ReceiptDraft empty() {
            return new ReceiptDraft(null, null, List.of(), null, null, null, "");
        }
    }
}
