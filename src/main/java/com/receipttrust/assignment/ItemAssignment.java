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

@Entity
@Table(name = "item_assignments")
public class ItemAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_item_id", nullable = false)
    private ReceiptItem receiptItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignee_id", nullable = false)
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    @Column(name = "share_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shareAmount;

    protected ItemAssignment() {
    }

    public ItemAssignment(ReceiptItem receiptItem, User assignee, SplitType splitType, BigDecimal shareAmount) {
        this.receiptItem = receiptItem;
        this.assignee = assignee;
        this.splitType = splitType;
        this.shareAmount = shareAmount;
    }

    public ReceiptItem getReceiptItem() {
        return receiptItem;
    }

    public User getAssignee() {
        return assignee;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }
}
