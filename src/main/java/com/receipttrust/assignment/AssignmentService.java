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
                                                              List<String> assigneeUsernames) {
        return assign(owner, receiptId, itemId, splitType, assigneeUsernames).stream()
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
                                       SplitType splitType, List<String> assigneeUsernames) {
        Receipt receipt = receiptService.requireOwned(owner, receiptId);
        if (receipt.isFinalized()) {
            throw new ApiExceptions.ConflictException("Receipt already finalized");
        }
        ReceiptItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Item not found"));
        if (!item.getReceipt().getId().equals(receiptId)) {
            throw new ApiExceptions.ResourceNotFoundException("Item not found on this receipt");
        }

        List<User> assignees = resolveAssignees(owner, assigneeUsernames);

        // Guard against over-assignment: existing shares + new shares must not exceed the line total.
        BigDecimal alreadyAssigned = assignmentRepository.findByReceiptItem(item).stream()
                .map(ItemAssignment::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BigDecimal> shares = computeShares(item.getLineTotal(), splitType, assignees.size());
        BigDecimal newTotal = shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        if (alreadyAssigned.add(newTotal).compareTo(item.getLineTotal()) > 0) {
            throw new ApiExceptions.ValidationException(
                    "Assignment exceeds the item's total amount");
        }

        List<ItemAssignment> created = new ArrayList<>();
        for (int i = 0; i < assignees.size(); i++) {
            created.add(assignmentRepository.save(
                    new ItemAssignment(item, assignees.get(i), splitType, shares.get(i))));
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

    private List<User> resolveAssignees(User owner, List<String> usernames) {
        List<User> assignees = new ArrayList<>();
        for (String username : usernames) {
            User assignee = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ApiExceptions.ValidationException(
                            "User not found: " + username));
            boolean isOwner = assignee.getId().equals(owner.getId());
            if (!isOwner && !friendService.areFriends(owner, assignee)) {
                throw new ApiExceptions.ValidationException(
                        "Can only assign items to accepted friends: " + username);
            }
            assignees.add(assignee);
        }
        return assignees;
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
