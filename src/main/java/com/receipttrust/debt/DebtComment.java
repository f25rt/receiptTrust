package com.receipttrust.debt;

import com.receipttrust.common.BaseEntity;
import com.receipttrust.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A comment on a debt, for creditor/debtor communication. */
@Entity
@Table(name = "debt_comments")
public class DebtComment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 1000)
    private String body;

    protected DebtComment() {
    }

    public DebtComment(Debt debt, User author, String body) {
        this.debt = debt;
        this.author = author;
        this.body = body;
    }

    public Debt getDebt() {
        return debt;
    }

    public User getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }
}
