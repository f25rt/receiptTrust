package com.receipttrust.payment;

import com.receipttrust.common.BaseEntity;
import com.receipttrust.debt.Debt;
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
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitter_id", nullable = false)
    private User submitter;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "balance_after", precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected Payment() {
    }

    public Payment(Debt debt, User submitter, BigDecimal amount, PaymentMethod method, String notes) {
        this.debt = debt;
        this.submitter = submitter;
        this.amount = amount;
        this.method = method;
        this.notes = notes;
        this.status = PaymentStatus.PENDING;
    }

    public Debt getDebt() {
        return debt;
    }

    public User getSubmitter() {
        return submitter;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getNotes() {
        return notes;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
