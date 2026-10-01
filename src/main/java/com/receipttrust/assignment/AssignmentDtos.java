package com.receipttrust.assignment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    /**
     * One assignment target: either a registered user (by username) or a
     * free-text label for a non-registered person. Exactly one is provided.
     */
    public record AssignTarget(
            String username,
            String label
    ) {
        public boolean isLabel() {
            return username == null || username.isBlank();
        }
    }

    public record AssignmentCreateRequest(
            @NotNull SplitType splitType,
            @NotEmpty List<AssignTarget> targets
    ) {
    }

    public record AssignmentResponse(
            Long id,
            Long receiptItemId,
            String assigneeName,
            boolean label,
            SplitType splitType,
            BigDecimal shareAmount,
            boolean confirmed
    ) {
        public static AssignmentResponse from(ItemAssignment a) {
            return new AssignmentResponse(a.getId(), a.getReceiptItem().getId(),
                    a.displayName(), a.isLabel(), a.getSplitType(), a.getShareAmount(), a.isConfirmed());
        }
    }

    /** An assignment awaiting the current user's confirmation. */
    public record PendingAssignment(
            Long assignmentId,
            Long receiptId,
            String storeName,
            String ownerUsername,
            String itemName,
            BigDecimal shareAmount
    ) {
        public static PendingAssignment from(ItemAssignment a) {
            var receipt = a.getReceiptItem().getReceipt();
            return new PendingAssignment(
                    a.getId(),
                    receipt.getId(),
                    receipt.getStoreName(),
                    receipt.getOwner().getUsername(),
                    a.getReceiptItem().getName(),
                    a.getShareAmount());
        }
    }
}
