package com.receipttrust.debt;

import com.receipttrust.common.BaseEntity;
import com.receipttrust.receipt.Receipt;
import com.receipttrust.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "debts")
public class Debt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creditor_id", nullable = false)
    private User creditor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debtor_id", nullable = false)
    private User debtor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "original_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "outstanding_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DebtStatus status;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "overdue_penalized", nullable = false)
    private boolean overduePenalized;

    @OneToMany(mappedBy = "debt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DebtItem> items = new ArrayList<>();

    protected Debt() {
    }

    public Debt(User creditor, User debtor, Receipt receipt, LocalDate purchaseDate,
                LocalDate dueDate, BigDecimal originalAmount) {
        this.creditor = creditor;
        this.debtor = debtor;
        this.receipt = receipt;
        this.purchaseDate = purchaseDate;
        this.dueDate = dueDate;
        this.originalAmount = originalAmount;
        this.outstandingAmount = originalAmount;
        this.status = DebtStatus.ACTIVE;
        this.overduePenalized = false;
    }

    public void addItem(DebtItem item) {
        items.add(item);
    }

    public User getCreditor() {
        return creditor;
    }

    public User getDebtor() {
        return debtor;
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public void setOutstandingAmount(BigDecimal outstandingAmount) {
        this.outstandingAmount = outstandingAmount;
    }

    public DebtStatus getStatus() {
        return status;
    }

    public void setStatus(DebtStatus status) {
        this.status = status;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public boolean isOverduePenalized() {
        return overduePenalized;
    }

    public void setOverduePenalized(boolean overduePenalized) {
        this.overduePenalized = overduePenalized;
    }

    public List<DebtItem> getItems() {
        return items;
    }
}
