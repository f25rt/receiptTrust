package com.receipttrust.assignment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    public record AssignmentCreateRequest(
            @NotNull SplitType splitType,
            @NotEmpty List<String> assigneeUsernames
    ) {
    }

    public record AssignmentResponse(
            Long id,
            Long receiptItemId,
            String assigneeUsername,
            SplitType splitType,
            BigDecimal shareAmount
    ) {
        public static AssignmentResponse from(ItemAssignment a) {
            return new AssignmentResponse(a.getId(), a.getReceiptItem().getId(),
                    a.getAssignee().getUsername(), a.getSplitType(), a.getShareAmount());
        }
    }
}
