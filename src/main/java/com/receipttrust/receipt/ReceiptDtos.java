package com.receipttrust.receipt;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ReceiptDtos {

    private ReceiptDtos() {
    }

    public record ReceiptResponse(
            Long id,
            Long ownerId,
            String storeName,
            LocalDate purchaseDate,
            String notes,
            String imageContentType,
            boolean hasImage,
            boolean finalized
    ) {
        public static ReceiptResponse from(Receipt r) {
            return new ReceiptResponse(r.getId(), r.getOwner().getId(), r.getStoreName(),
                    r.getPurchaseDate(), r.getNotes(), r.getImageContentType(),
                    r.hasImage(), r.isFinalized());
        }
    }

    public record ItemCreateRequest(
            @NotBlank String name,
            @Min(1) int quantity,
            @NotNull @PositiveOrZero BigDecimal unitPrice
    ) {
    }

    public record ItemResponse(
            Long id,
            String name,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
        public static ItemResponse from(ReceiptItem i) {
            return new ItemResponse(i.getId(), i.getName(), i.getQuantity(),
                    i.getUnitPrice(), i.getLineTotal());
        }
    }
}
