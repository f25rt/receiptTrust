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

    @Test
    void parsesQtyColumnWithPriceAndAmountColumns() {
        // Layout like "The Daily Bite": Qty | Item | Price | Amount, peso amounts, VAT + service.
        String text = String.join("\n",
                "THE DAILY BITE",
                "Qty  Item Description        Price      Amount",
                "1    Classic Burger          295.00     295.00",
                "1    Truffle Fries           185.00     185.00",
                "2    Iced Latte              165.00     330.00",
                "1    Chocolate Lava Cake     220.00     220.00",
                "Subtotal                                1,030.00",
                "Service Charge (10%)                    103.00",
                "VAT (12%)                               135.96",
                "TOTAL                                   1,268.96");

        OcrDtos.ReceiptDraft draft = ReceiptTextParser.parse(text);

        assertThat(draft.storeName()).isEqualTo("THE DAILY BITE");
        assertThat(draft.serviceCharge()).isEqualByComparingTo("103.00");
        assertThat(draft.total()).isEqualByComparingTo("1268.96");
        assertThat(draft.items()).hasSize(4);

        OcrDtos.ParsedItem burger = draft.items().get(0);
        assertThat(burger.name()).isEqualTo("Classic Burger");
        assertThat(burger.quantity()).isEqualTo(1);
        assertThat(burger.unitPrice()).isEqualByComparingTo("295.00");

        OcrDtos.ParsedItem latte = draft.items().get(2);
        assertThat(latte.name()).isEqualTo("Iced Latte");
        assertThat(latte.quantity()).isEqualTo(2);
        // Price column is the unit price, not the 330.00 amount.
        assertThat(latte.unitPrice()).isEqualByComparingTo("165.00");
    }
}
