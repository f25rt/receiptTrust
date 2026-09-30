package com.receipttrust.receipt;

import com.receipttrust.assignment.ItemAssignment;
import com.receipttrust.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "receipt_items")
public class ReceiptItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @OneToMany(mappedBy = "receiptItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemAssignment> assignments = new ArrayList<>();

    protected ReceiptItem() {
    }

    public ReceiptItem(Receipt receipt, String name, int quantity, BigDecimal unitPrice) {
        this.receipt = receipt;
        this.name = name;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        recomputeLineTotal();
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        recomputeLineTotal();
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public List<ItemAssignment> getAssignments() {
        return assignments;
    }

    private void recomputeLineTotal() {
        if (unitPrice != null) {
            this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
