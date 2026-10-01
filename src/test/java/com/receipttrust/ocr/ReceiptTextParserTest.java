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
    void parsesBillWithStoreNameAtBottomAndCommaDecimals() {
        // Real OCR.space output for the "SUNBURST" receipt: generic "BILL" header,
        // store name on the "N item(s)" summary line, and COMMA decimals (308,00).
        String text = String.join("\n",
                "B I L L",
                "Table 18",
                "Sep 07, 2026 (Mon)        Bill #: 2158",
                "3  Double - Thigh    308,00    924,00",
                "1  Sotanghon Guisado - Smal   218,00",
                "1  SUNDOU - TD              268,00",
                "4  SERVICE WATER -GLA 0,00    0,00",
                "1  Plain Rice - Mould         48,00",
                "SUBTOTAL                    1.458,00",
                "SERVICE CHARGE    5%           65,09",
                "TOTAL                       1.523,09",
                "SUNBURST     10 item(s)     7:05 PM");

        OcrDtos.ReceiptDraft draft = ReceiptTextParser.parse(text);

        // Store name comes from the bottom summary line, NOT a leaked item line.
        assertThat(draft.storeName()).isEqualTo("SUNBURST");
        // Comma-decimals parse correctly.
        assertThat(draft.serviceCharge()).isEqualByComparingTo("65.09");
        assertThat(draft.total()).isEqualByComparingTo("1523.09");

        // Double - Thigh: qty 3, unit 308.00 (derived from 924.00 / 308.00).
        OcrDtos.ParsedItem thigh = draft.items().get(0);
        assertThat(thigh.name()).contains("Double");
        assertThat(thigh.quantity()).isEqualTo(3);
        assertThat(thigh.unitPrice()).isEqualByComparingTo("308.00");

        // A single-amount line keeps qty 1 and that amount as the unit price.
        assertThat(draft.items()).anySatisfy(it -> {
            assertThat(it.name()).contains("Sotanghon");
            assertThat(it.quantity()).isEqualTo(1);
            assertThat(it.unitPrice()).isEqualByComparingTo("218.00");
        });
        // The "Plain Rice" line must be an item, not leaked into the store name.
        assertThat(draft.items()).anySatisfy(it -> {
            assertThat(it.name()).contains("Plain Rice");
            assertThat(it.unitPrice()).isEqualByComparingTo("48.00");
        });
    }

    @Test
    void trustsExplicitQuantityWhenOcrMisreadsLineTotal() {
        // Exact OCR.space output for the SUNBURST receipt (tab-separated), where the
        // line total was misread 924.00 -> 724.00. The explicit leading "3" must win
        // over deriving 724/308 = 2.
        String rawText = "B\tILL\t\r\nTable 18\t\r\nSep 07, 2026 (Mon)\tBill N: 2158\t\r\n"
                + "3 Double - Thigh\t308.00\t724.00\t\r\n"
                + "1 Sotanghon Guisado - Smal\t218.00\t\r\n"
                + "SUNDOU - 10\t268.00\t\r\n"
                + "SERVICE WATER -GLA 0.00\t0.00\t\r\n"
                + "1 Plain Rice - Mould\t48,00\t\r\n"
                + "SUBTOTAL\t1,458.00\t\r\n"
                + "SERVICE CHARGE\t52\t65.09\t\r\n"
                + "TOTAL\t1,523.09\t\r\n"
                + "7:05 PM\t\r\n"
                + "SUNBURST\t10 item(%)\t\r\n";

        OcrDtos.ReceiptDraft draft = ReceiptTextParser.parse(rawText);

        assertThat(draft.storeName()).isEqualTo("SUNBURST");
        assertThat(draft.total()).isEqualByComparingTo("1523.09");
        assertThat(draft.serviceCharge()).isEqualByComparingTo("65.09");

        OcrDtos.ParsedItem thigh = draft.items().get(0);
        assertThat(thigh.name()).contains("Double");
        assertThat(thigh.quantity()).isEqualTo(3);          // explicit "3", not derived 2
        assertThat(thigh.unitPrice()).isEqualByComparingTo("308.00");
    }

    @Test
    void storeNameEmptyWhenNoConfidentCandidate() {
        // Only item lines and a generic header -> store name should be null, not a
        // leaked item line.
        OcrDtos.ReceiptDraft draft = ReceiptTextParser.parse(String.join("\n",
                "BILL",
                "1  Plain Rice - Mould   48,00",
                "TOTAL   48,00"));
        assertThat(draft.storeName()).isNull();
        assertThat(draft.items()).anySatisfy(it -> assertThat(it.name()).contains("Plain Rice"));
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
