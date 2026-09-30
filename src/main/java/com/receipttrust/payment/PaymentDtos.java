package com.receipttrust.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentCreateRequest(
            @NotNull @Positive BigDecimal amount,
            @NotNull PaymentMethod method,
            String notes
    ) {
    }

    public record PaymentResponse(
            Long id,
            Long debtId,
            String submitterUsername,
            BigDecimal amount,
            PaymentMethod method,
            String notes,
            PaymentStatus status,
            BigDecimal balanceAfter,
            Instant createdAt,
            Instant decidedAt
    ) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getDebt().getId(),
                    p.getSubmitter().getUsername(), p.getAmount(), p.getMethod(), p.getNotes(),
                    p.getStatus(), p.getBalanceAfter(), p.getCreatedAt(), p.getDecidedAt());
        }
    }
}
