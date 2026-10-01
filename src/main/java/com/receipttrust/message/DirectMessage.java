package com.receipttrust.message;

import com.receipttrust.common.BaseEntity;
import com.receipttrust.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A direct message from one user to another. Messaging is only permitted
 * between accepted friends.
 */
@Entity
@Table(name = "direct_messages")
public class DirectMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "read_by_recipient", nullable = false)
    private boolean readByRecipient;

    protected DirectMessage() {
    }

    public DirectMessage(User sender, User recipient, String body) {
        this.sender = sender;
        this.recipient = recipient;
        this.body = body;
        this.readByRecipient = false;
    }

    public User getSender() {
        return sender;
    }

    public User getRecipient() {
        return recipient;
    }

    public String getBody() {
        return body;
    }

    public boolean isReadByRecipient() {
        return readByRecipient;
    }

    public void setReadByRecipient(boolean readByRecipient) {
        this.readByRecipient = readByRecipient;
    }
}
