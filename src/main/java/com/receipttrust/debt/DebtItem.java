package com.receipttrust.debt;

import com.receipttrust.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Immutable snapshot of an item's contribution to a debt, used by the
 * "Why do I owe this?" explanation so it stays accurate even if the source
 * receipt were ever changed.
 */
@Entity
@Table(name = "debt_items")
public class DebtItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected DebtItem() {
    }

    public DebtItem(Debt debt, String itemName, BigDecimal amount) {
        this.debt = debt;
        this.itemName = itemName;
        this.amount = amount;
    }

    public Debt getDebt() {
        return debt;
    }

    public String getItemName() {
        return itemName;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
