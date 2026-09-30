package com.receipttrust.debt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DebtDtos {

    private DebtDtos() {
    }

    public record DebtSummary(
            Long debtId,
            String counterpartyUsername,
            BigDecimal outstandingAmount,
            BigDecimal originalAmount,
            DebtStatus status,
            LocalDate dueDate
    ) {
    }

    public record DashboardResponse(
            List<DebtSummary> owedToMe,
            List<DebtSummary> iOwe,
            BigDecimal totalOwedToMe,
            BigDecimal totalIOwe,
            long activeDebts,
            long settledDebts
    ) {
    }

    public record ExplanationItem(
            String itemName,
            BigDecimal amount
    ) {
    }

    public record ExplanationResponse(
            Long debtId,
            String paidByUsername,
            String storeName,
            LocalDate purchaseDate,
            List<ExplanationItem> items,
            Long receiptId,
            String receiptImageUrl,
            BigDecimal totalDebt
    ) {
    }

    public record TimelineEvent(
            Instant at,
            String type,
            String detail,
            BigDecimal amount,
            BigDecimal remainingBalance
    ) {
    }

    public record HistoryResponse(
            Long debtId,
            List<TimelineEvent> events
    ) {
    }
}
