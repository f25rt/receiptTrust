package com.receipttrust.ocr;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptTextParserTest {

    @Test
    void parsesStoreItemsServiceChargeAndTotal() {
        String text = String.join("\n",
                "Blue Bottle Coffee",
                "Bakery & Espresso Bar",
                "2x Oat Milk Latte      13.00",
                "1 Avocado Toast        16.50",
                "Almond Croissant        6.50",
                "Subtotal               36.00",
                "Service Charge          6.48",
                "Total                  46.80",
                "Thank you!");

        OcrDtos.ReceiptDraft draft = ReceiptTextParser.parse(text);

        assertThat(draft.storeName()).isEqualTo("Blue Bottle Coffee");
        assertThat(draft.serviceCharge()).isEqualByComparingTo("6.48");
        assertThat(draft.total()).isEqualByComparingTo("46.80");

        assertThat(draft.items()).hasSize(3);
        // Quantity 2 with line total 13.00 -> unit price 6.50
        OcrDtos.ParsedItem latte = draft.items().get(0);
        assertThat(latte.name()).contains("Oat Milk Latte");
        assertThat(latte.quantity()).isEqualTo(2);
        assertThat(latte.unitPrice()).isEqualByComparingTo("6.50");

        OcrDtos.ParsedItem toast = draft.items().get(1);
        assertThat(toast.name()).contains("Avocado Toast");
        assertThat(toast.quantity()).isEqualTo(1);
        assertThat(toast.unitPrice()).isEqualByComparingTo("16.50");
    }

    @Test
    void skipsNonItemLinesAndHandlesEmptyInput() {
        assertThat(ReceiptTextParser.parse("").items()).isEmpty();
        assertThat(ReceiptTextParser.parse(null).items()).isEmpty();

        OcrDtos.ReceiptDraft draft = ReceiptTextParser.parse(String.join("\n",
                "Corner Deli",
                "Tax                 1.20",
                "Cash                20.00",
                "Change               3.20"));
        // Tax/cash/change are skipped, not treated as items.
        assertThat(draft.items()).isEmpty();
    }

    @Test
    void keepsLargestTotalWhenMultiplePresent() {
        OcrDtos.ReceiptDraft draft = ReceiptTextParser.parse(String.join("\n",
                "Shop",
                "Subtotal   10.00",
                "Total      12.00"));
        assertThat(draft.total()).isEqualByComparingTo("12.00");
        assertThat(draft.serviceCharge()).isNull();
        assertThat(draft.items().stream().map(OcrDtos.ParsedItem::unitPrice))
                .doesNotContain(new BigDecimal("12.00"));
    }
}
