package com.receipttrust.assignment;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.friend.FriendService;
import com.receipttrust.notification.NotificationService;
import com.receipttrust.notification.NotificationType;
import com.receipttrust.receipt.Receipt;
import com.receipttrust.receipt.ReceiptItem;
import com.receipttrust.receipt.ReceiptItemRepository;
import com.receipttrust.receipt.ReceiptService;
import com.receipttrust.user.User;
import com.receipttrust.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class AssignmentService {

    private final ItemAssignmentRepository assignmentRepository;
    private final ReceiptItemRepository itemRepository;
    private final ReceiptService receiptService;
    private final FriendService friendService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AssignmentService(ItemAssignmentRepository assignmentRepository,
                             ReceiptItemRepository itemRepository,
                             ReceiptService receiptService,
                             FriendService friendService,
                             UserRepository userRepository,
                             NotificationService notificationService) {
        this.assignmentRepository = assignmentRepository;
        this.itemRepository = itemRepository;
        this.receiptService = receiptService;
        this.friendService = friendService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public List<AssignmentDtos.AssignmentResponse> assignDtos(User owner, Long receiptId, Long itemId,
                                                              SplitType splitType,
                                                              List<AssignmentDtos.AssignTarget> targets) {
        return assign(owner, receiptId, itemId, splitType, targets).stream()
                .map(AssignmentDtos.AssignmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentDtos.AssignmentResponse> listForItemDtos(User owner, Long receiptId, Long itemId) {
        return listForItem(owner, receiptId, itemId).stream()
                .map(AssignmentDtos.AssignmentResponse::from)
                .toList();
    }

    @Transactional
    public List<ItemAssignment> assign(User owner, Long receiptId, Long itemId,
                                       SplitType splitType, List<AssignmentDtos.AssignTarget> targets) {
        Receipt receipt = receiptService.requireOwned(owner, receiptId);
        if (receipt.isFinalized()) {
            throw new ApiExceptions.ConflictException("Receipt already finalized");
        }
        ReceiptItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Item not found"));
        if (!item.getReceipt().getId().equals(receiptId)) {
            throw new ApiExceptions.ResourceNotFoundException("Item not found on this receipt");
        }

        List<ResolvedTarget> resolved = resolveTargets(owner, targets);

        // Guard against over-assignment: existing shares + new shares must not exceed the line total.
        BigDecimal alreadyAssigned = assignmentRepository.findByReceiptItem(item).stream()
                .map(ItemAssignment::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BigDecimal> shares = computeShares(item.getLineTotal(), splitType, resolved.size());
        BigDecimal newTotal = shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        if (alreadyAssigned.add(newTotal).compareTo(item.getLineTotal()) > 0) {
            throw new ApiExceptions.ValidationException(
                    "Assignment exceeds the item's total amount");
        }

        List<ItemAssignment> created = new ArrayList<>();
        for (int i = 0; i < resolved.size(); i++) {
            ResolvedTarget t = resolved.get(i);
            ItemAssignment assignment = t.user != null
                    ? new ItemAssignment(item, t.user, splitType, shares.get(i))
                    : new ItemAssignment(item, t.label, splitType, shares.get(i));
            // A registered user who is not yet a friend must confirm the assignment
            // before the receipt can be finalized.
            assignment.setConfirmed(!t.needsConfirmation);
            ItemAssignment saved = assignmentRepository.save(assignment);
            created.add(saved);

            if (t.needsConfirmation) {
                notificationService.notify(t.user, NotificationType.ASSIGNMENT_CONFIRM_REQUEST,
                        owner.getUsername() + " assigned you \"" + item.getName() + "\" ("
                                + shares.get(i) + "). Confirm it's yours so they can finalize the split.");
            }
        }
        return created;
    }

    /**
     * The assignee confirms a pending assignment is theirs. Only the assignee may
     * confirm. Notifies the receipt owner.
     */
    @Transactional
    public void confirmAssignment(User assignee, Long assignmentId) {
        ItemAssignment assignment = requirePendingForAssignee(assignee, assignmentId);
        assignment.setConfirmed(true);
        assignmentRepository.save(assignment);

        Receipt receipt = assignment.getReceiptItem().getReceipt();
        User owner = receipt.getOwner();
        String itemName = assignment.getReceiptItem().getName();

        // If this was the last outstanding confirmation, the receipt can now be
        // finalized — tell the owner it's ready.
        boolean allConfirmed = assignmentRepository.findUnconfirmedByReceipt(receipt).isEmpty();
        String message = allConfirmed
                ? assignee.getUsername() + " confirmed \"" + itemName + "\". All items are confirmed — "
                        + "the receipt at " + receipt.getStoreName() + " is ready to finalize."
                : assignee.getUsername() + " confirmed \"" + itemName + "\" is theirs.";
        notificationService.notify(owner, NotificationType.ASSIGNMENT_CONFIRMED, message);
    }

    /**
     * The assignee declines a pending assignment: it is removed from the receipt.
     * Notifies the receipt owner.
     */
    @Transactional
    public void declineAssignment(User assignee, Long assignmentId) {
        ItemAssignment assignment = requirePendingForAssignee(assignee, assignmentId);
        String itemName = assignment.getReceiptItem().getName();
        User owner = assignment.getReceiptItem().getReceipt().getOwner();
        assignmentRepository.delete(assignment);
        notificationService.notify(owner, NotificationType.ASSIGNMENT_DECLINED,
                assignee.getUsername() + " declined \"" + itemName + "\". It was removed from the split.");
    }

    /** Assignments awaiting the given user's confirmation. */
    @Transactional(readOnly = true)
    public List<AssignmentDtos.PendingAssignment> pendingForAssignee(User assignee) {
        return assignmentRepository.findPendingForAssignee(assignee.getId()).stream()
                .map(AssignmentDtos.PendingAssignment::from)
                .toList();
    }

    private ItemAssignment requirePendingForAssignee(User assignee, Long assignmentId) {
        ItemAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Assignment not found"));
        if (assignment.getAssignee() == null
                || !assignment.getAssignee().getId().equals(assignee.getId())) {
            throw new ApiExceptions.ForbiddenException("Not your assignment to confirm");
        }
        if (assignment.getReceiptItem().getReceipt().isFinalized()) {
            throw new ApiExceptions.ConflictException("Receipt is already finalized");
        }
        return assignment;
    }

    @Transactional
    public void remove(User owner, Long receiptId, Long itemId, Long assignmentId) {
        Receipt receipt = receiptService.requireOwned(owner, receiptId);
        if (receipt.isFinalized()) {
            throw new ApiExceptions.ConflictException("Receipt already finalized");
        }
        ItemAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Assignment not found"));
        if (!assignment.getReceiptItem().getId().equals(itemId)) {
            throw new ApiExceptions.ResourceNotFoundException("Assignment not found for this item");
        }
        assignmentRepository.delete(assignment);
    }

    @Transactional(readOnly = true)
    public List<ItemAssignment> listForItem(User owner, Long receiptId, Long itemId) {
        receiptService.requireOwned(owner, receiptId);
        ReceiptItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Item not found"));
        return assignmentRepository.findByReceiptItem(item);
    }

    /**
     * A resolved assignment target: either a registered user or a label string.
     * {@code needsConfirmation} is true for a registered user who is not yet an
     * accepted friend of the owner (and is not the owner).
     */
    private record ResolvedTarget(User user, String label, boolean needsConfirmation) {
    }

    private List<ResolvedTarget> resolveTargets(User owner, List<AssignmentDtos.AssignTarget> targets) {
        List<ResolvedTarget> resolved = new ArrayList<>();
        for (AssignmentDtos.AssignTarget target : targets) {
            if (target.isLabel()) {
                String label = target.label() == null ? "" : target.label().strip();
                if (label.isEmpty()) {
                    throw new ApiExceptions.ValidationException("Label name is required");
                }
                resolved.add(new ResolvedTarget(null, label, false));
            } else {
                User assignee = userRepository.findByUsername(target.username())
                        .orElseThrow(() -> new ApiExceptions.ValidationException(
                                "User not found: " + target.username()));
                boolean isOwner = assignee.getId().equals(owner.getId());
                // Friends (and the owner) are auto-confirmed; anyone else must confirm.
                boolean needsConfirmation = !isOwner && !friendService.areFriends(owner, assignee);
                resolved.add(new ResolvedTarget(assignee, null, needsConfirmation));
            }
        }
        return resolved;
    }



    private List<BigDecimal> computeShares(BigDecimal lineTotal, SplitType splitType, int count) {
        if (splitType == SplitType.INDIVIDUAL) {
            if (count != 1) {
                throw new ApiExceptions.ValidationException(
                        "Individual assignment requires exactly one assignee");
            }
            return List.of(lineTotal);
        }
        return SplitCalculator.equalSplit(lineTotal, count);
    }
}
