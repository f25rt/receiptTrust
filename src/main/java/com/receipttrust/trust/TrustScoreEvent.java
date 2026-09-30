package com.receipttrust.trust;

import com.receipttrust.common.BaseEntity;
import com.receipttrust.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "trust_score_events")
public class TrustScoreEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int delta;

    @Column(nullable = false)
    private String reason;

    @Column(name = "resulting_score", nullable = false)
    private int resultingScore;

    protected TrustScoreEvent() {
    }

    public TrustScoreEvent(User user, int delta, String reason, int resultingScore) {
        this.user = user;
        this.delta = delta;
        this.reason = reason;
        this.resultingScore = resultingScore;
    }

    public User getUser() {
        return user;
    }

    public int getDelta() {
        return delta;
    }

    public String getReason() {
        return reason;
    }

    public int getResultingScore() {
        return resultingScore;
    }
}
