package com.receipttrust.debt;

import com.receipttrust.assignment.ItemAssignment;
import com.receipttrust.assignment.ItemAssignmentRepository;
import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.config.DebtProperties;
import com.receipttrust.notification.NotificationService;
import com.receipttrust.notification.NotificationType;
import com.receipttrust.receipt.Receipt;
import com.receipttrust.receipt.ReceiptRepository;
import com.receipttrust.receipt.ReceiptService;
import com.receipttrust.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DebtService {

    private final DebtRepository debtRepository;
    private final ItemAssignmentRepository assignmentRepository;
    private final ReceiptRepository receiptRepository;
    private final ReceiptService receiptService;
    private final NotificationService notificationService;
    private final DebtProperties debtProperties;

    public DebtService(DebtRepository debtRepository,
                       ItemAssignmentRepository assignmentRepository,
                       ReceiptRepository receiptRepository,
                       ReceiptService receiptService,
                       NotificationService notificationService,
                       DebtProperties debtProperties) {
        this.debtRepository = debtRepository;
        this.assignmentRepository = assignmentRepository;
        this.receiptRepository = receiptRepository;
        this.receiptService = receiptService;
        this.notificationService = notificationService;
        this.debtProperties = debtProperties;
    }

    /**
     * Finalizes a receipt: aggregates each debtor's shares across all items into
     * a single debt owed to the receipt owner (creditor). Shares assigned to the
     * owner are excluded. Idempotency is enforced by the finalized flag.
     */
    @Transactional
    public List<Debt> finalizeReceipt(User owner, Long receiptId, LocalDate customDueDate) {
        Receipt receipt = receiptService.requireOwned(owner, receiptId);
        if (receipt.isFinalized()) {
            throw new ApiExceptions.ConflictException("Receipt already finalized");
        }

        List<ItemAssignment> assignments = assignmentRepository.findByReceipt(receipt);

        // Aggregate by debtor key (registered user or label), excluding the owner's
        // own shares. Repeated assignments to the same debtor accumulate here.
        Map<String, DebtorAccumulator> byDebtor = new LinkedHashMap<>();
        for (ItemAssignment a : assignments) {
            User assignee = a.getAssignee();
            if (assignee != null && assignee.getId().equals(owner.getId())) {
                continue;
            }
            byDebtor.computeIfAbsent(a.debtorKey(), k -> new DebtorAccumulator(assignee, a.getAssigneeLabel()))
                    .add(a.getReceiptItem().getName(), a.getShareAmount());
        }

        LocalDate dueDate = customDueDate != null
                ? customDueDate
                : receipt.getPurchaseDate().plusDays(debtProperties.getDefaultTermDays());

        List<Debt> created = new ArrayList<>();
        for (DebtorAccumulator acc : byDebtor.values()) {
            Debt debt = acc.debtor != null
                    ? new Debt(owner, acc.debtor, receipt, receipt.getPurchaseDate(), dueDate, acc.total)
                    : new Debt(owner, acc.label, receipt, receipt.getPurchaseDate(), dueDate, acc.total);
            acc.items.forEach((name, amount) -> debt.addItem(new DebtItem(debt, name, amount)));
            created.add(debtRepository.save(debt));

            // Only registered debtors get an in-app notification; labels have no account.
            if (acc.debtor != null) {
                notificationService.notify(acc.debtor, NotificationType.DEBT_CREATED,
                        "You owe " + owner.getUsername() + " " + acc.total
                                + " for receipt at " + receipt.getStoreName());
            }
        }

        receipt.setFinalized(true);
        receiptRepository.save(receipt);
        return created;
    }

    /**
     * Finalizes the receipt and returns debtor-facing summaries built inside the
     * transaction, avoiding lazy-loading issues in the controller layer.
     */
    @Transactional
    public List<DebtDtos.DebtSummary> finalizeReceiptSummaries(User owner, Long receiptId, LocalDate dueDate) {
        return finalizeReceipt(owner, receiptId, dueDate).stream()
                .map(d -> new DebtDtos.DebtSummary(d.getId(), d.debtorDisplayName(), d.isLabelDebt(),
                        d.getOutstandingAmount(), d.getOriginalAmount(), d.getStatus(), d.getDueDate()))
                .toList();
    }

    public Debt require(Long debtId) {
        return debtRepository.findById(debtId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Debt not found"));
    }

    public Debt requireParticipant(User user, Long debtId) {
        Debt debt = require(debtId);
        boolean isCreditor = debt.getCreditor().getId().equals(user.getId());
        boolean isDebtor = debt.getDebtor() != null && debt.getDebtor().getId().equals(user.getId());
        if (!isCreditor && !isDebtor) {
            throw new ApiExceptions.ForbiddenException("Not a participant in this debt");
        }
        return debt;
    }

    /** Transactional summary for a single debt, safe for controller serialization. */
    @Transactional(readOnly = true)
    public DebtDtos.DebtSummary summaryFor(User user, Long debtId) {
        Debt debt = requireParticipant(user, debtId);
        boolean isCreditor = debt.getCreditor().getId().equals(user.getId());
        String counterparty = isCreditor ? debt.debtorDisplayName() : debt.getCreditor().getUsername();
        boolean counterpartyIsLabel = isCreditor && debt.isLabelDebt();
        return new DebtDtos.DebtSummary(debt.getId(), counterparty, counterpartyIsLabel,
                debt.getOutstandingAmount(), debt.getOriginalAmount(), debt.getStatus(), debt.getDueDate());
    }

    /** Accumulates a single debtor's (user or label) item shares and running total. */
    private static final class DebtorAccumulator {
        private final User debtor;
        private final String label;
        private final Map<String, BigDecimal> items = new LinkedHashMap<>();
        private BigDecimal total = BigDecimal.ZERO;

        DebtorAccumulator(User debtor, String label) {
            this.debtor = debtor;
            this.label = label;
        }

        void add(String itemName, BigDecimal amount) {
            items.merge(itemName, amount, BigDecimal::add);
            total = total.add(amount);
        }
    }
}
