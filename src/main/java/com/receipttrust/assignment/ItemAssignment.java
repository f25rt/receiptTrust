package com.receipttrust.assignment;

import com.receipttrust.common.BaseEntity;
import com.receipttrust.receipt.ReceiptItem;
import com.receipttrust.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Assigns a share of a receipt item to either a registered {@link User}
 * ({@code assignee} set) or a free-text external person ({@code assigneeLabel}
 * set). Exactly one of the two is populated.
 */
@Entity
@Table(name = "item_assignments")
public class ItemAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_item_id", nullable = false)
    private ReceiptItem receiptItem;

    /** Registered assignee, or null when this is a label assignment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /** Free-text assignee name for non-registered people, or null. */
    @Column(name = "assignee_label")
    private String assigneeLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    @Column(name = "share_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shareAmount;

    /**
     * Whether the assignee has agreed this item is theirs. Friends (and the owner)
     * are auto-confirmed; a registered user who is not yet a friend starts
     * unconfirmed and must confirm before the receipt can be finalized. Labels
     * (non-registered) are always confirmed since they have no account to ask.
     */
    @Column(name = "confirmed", nullable = false)
    private boolean confirmed = true;

    protected ItemAssignment() {
    }

    /** Registered-user assignment. */
    public ItemAssignment(ReceiptItem receiptItem, User assignee, SplitType splitType, BigDecimal shareAmount) {
        this.receiptItem = receiptItem;
        this.assignee = assignee;
        this.splitType = splitType;
        this.shareAmount = shareAmount;
    }

    /** Label (non-registered) assignment. */
    public ItemAssignment(ReceiptItem receiptItem, String assigneeLabel, SplitType splitType, BigDecimal shareAmount) {
        this.receiptItem = receiptItem;
        this.assigneeLabel = assigneeLabel;
        this.splitType = splitType;
        this.shareAmount = shareAmount;
    }

    public boolean isLabel() {
        return assignee == null;
    }

    /** Stable key identifying the debtor: user id string, or "label:<name>". */
    public String debtorKey() {
        return assignee != null ? "user:" + assignee.getId() : "label:" + assigneeLabel;
    }

    public ReceiptItem getReceiptItem() {
        return receiptItem;
    }

    public User getAssignee() {
        return assignee;
    }

    public String getAssigneeLabel() {
        return assigneeLabel;
    }

    public String displayName() {
        return assignee != null ? assignee.getUsername() : assigneeLabel;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}
