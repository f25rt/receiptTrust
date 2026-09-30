package com.receipttrust.assignment;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.friend.FriendService;
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

    public AssignmentService(ItemAssignmentRepository assignmentRepository,
                             ReceiptItemRepository itemRepository,
                             ReceiptService receiptService,
                             FriendService friendService,
                             UserRepository userRepository) {
        this.assignmentRepository = assignmentRepository;
        this.itemRepository = itemRepository;
        this.receiptService = receiptService;
        this.friendService = friendService;
        this.userRepository = userRepository;
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
            created.add(assignmentRepository.save(assignment));
        }
        return created;
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

    /** A resolved assignment target: either a registered user or a label string. */
    private record ResolvedTarget(User user, String label) {
    }

    private List<ResolvedTarget> resolveTargets(User owner, List<AssignmentDtos.AssignTarget> targets) {
        List<ResolvedTarget> resolved = new ArrayList<>();
        for (AssignmentDtos.AssignTarget target : targets) {
            if (target.isLabel()) {
                String label = target.label() == null ? "" : target.label().strip();
                if (label.isEmpty()) {
                    throw new ApiExceptions.ValidationException("Label name is required");
                }
                resolved.add(new ResolvedTarget(null, label));
            } else {
                User assignee = userRepository.findByUsername(target.username())
                        .orElseThrow(() -> new ApiExceptions.ValidationException(
                                "User not found: " + target.username()));
                boolean isOwner = assignee.getId().equals(owner.getId());
                if (!isOwner && !friendService.areFriends(owner, assignee)) {
                    throw new ApiExceptions.ValidationException(
                            "Can only assign items to accepted friends: " + target.username());
                }
                resolved.add(new ResolvedTarget(assignee, null));
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
