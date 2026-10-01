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
import com.receipttrust.trust.TrustScoreService;
import com.receipttrust.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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
    private final TrustScoreService trustScoreService;
    private final DebtCommentRepository commentRepository;

    public DebtService(DebtRepository debtRepository,
                       ItemAssignmentRepository assignmentRepository,
                       ReceiptRepository receiptRepository,
                       ReceiptService receiptService,
                       NotificationService notificationService,
                       DebtProperties debtProperties,
                       TrustScoreService trustScoreService,
                       DebtCommentRepository commentRepository) {
        this.debtRepository = debtRepository;
        this.assignmentRepository = assignmentRepository;
        this.receiptRepository = receiptRepository;
        this.receiptService = receiptService;
        this.notificationService = notificationService;
        this.debtProperties = debtProperties;
        this.trustScoreService = trustScoreService;
        this.commentRepository = commentRepository;
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
                        owner.getUsername() + " tagged you in a debt of " + acc.total
                                + " for the receipt at " + receipt.getStoreName());
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

    /**
     * Creditor marks the debt as fully paid. Because only the lender approves,
     * this settles immediately (no debtor approval), applies the trust update,
     * and notifies the debtor.
     */
    @Transactional
    public DebtDtos.DebtSummary markAsPaidByCreditor(User user, Long debtId) {
        Debt debt = require(debtId);
        if (!debt.getCreditor().getId().equals(user.getId())) {
            throw new ApiExceptions.ForbiddenException("Only the lender can mark this debt as paid");
        }
        if (debt.getStatus() == DebtStatus.SETTLED) {
            throw new ApiExceptions.ConflictException("Debt is already settled");
        }
        debt.setOutstandingAmount(BigDecimal.ZERO);
        debt.setStatus(DebtStatus.SETTLED);
        debt.setSettledAt(Instant.now());
        debtRepository.save(debt);

        trustScoreService.onDebtSettled(debt);

        if (debt.getDebtor() != null) {
            notificationService.notify(debt.getDebtor(), NotificationType.DEBT_SETTLED,
                    user.getUsername() + " marked your debt as fully paid");
        }
        return new DebtDtos.DebtSummary(debt.getId(), debt.debtorDisplayName(), debt.isLabelDebt(),
                debt.getOutstandingAmount(), debt.getOriginalAmount(), debt.getStatus(), debt.getDueDate());
    }

    @Transactional
    public DebtDtos.CommentResponse addComment(User user, Long debtId, String body) {
        Debt debt = requireParticipant(user, debtId);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) {
            throw new ApiExceptions.ValidationException("Comment cannot be empty");
        }
        DebtComment comment = commentRepository.save(new DebtComment(debt, user, text));

        // Notify the other participant.
        boolean authorIsCreditor = debt.getCreditor().getId().equals(user.getId());
        User recipient = authorIsCreditor ? debt.getDebtor() : debt.getCreditor();
        if (recipient != null) {
            notificationService.notify(recipient, NotificationType.DEBT_COMMENT,
                    user.getUsername() + " commented on debt #" + debt.getId() + ": " + preview(text));
        }
        return toCommentResponse(comment, user);
    }

    @Transactional(readOnly = true)
    public List<DebtDtos.CommentResponse> listComments(User user, Long debtId) {
        Debt debt = requireParticipant(user, debtId);
        return commentRepository.findByDebtOrderByCreatedAtAsc(debt).stream()
                .map(c -> toCommentResponse(c, user))
                .toList();
    }

    private DebtDtos.CommentResponse toCommentResponse(DebtComment c, User viewer) {
        return new DebtDtos.CommentResponse(
                c.getId(),
                c.getAuthor().getUsername(),
                c.getAuthor().getId().equals(viewer.getId()),
                c.getBody(),
                c.getCreatedAt());
    }

    private static String preview(String text) {
        return text.length() > 60 ? text.substring(0, 57) + "..." : text;
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
