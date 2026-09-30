package com.receipttrust.trust;

import com.receipttrust.config.TrustProperties;
import com.receipttrust.debt.Debt;
import com.receipttrust.user.User;
import com.receipttrust.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Applies trust-score deltas and records an audit event for every change.
 * Points (from the spec):
 *   full settlement +15, early +10, on-time +5, late -10, overdue 30+ -25.
 * Score is clamped to the configured [min, max] range.
 */
@Service
public class TrustScoreService {

    static final int FULL_SETTLEMENT = 15;
    static final int EARLY = 10;
    static final int ON_TIME = 5;
    static final int LATE = -10;
    static final int OVERDUE = -25;

    private final UserRepository userRepository;
    private final TrustScoreEventRepository eventRepository;
    private final TrustProperties properties;

    public TrustScoreService(UserRepository userRepository,
                             TrustScoreEventRepository eventRepository,
                             TrustProperties properties) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.properties = properties;
    }

    /**
     * Called when an approved payment fully settles a debt. Applies the
     * full-settlement bonus plus a timing adjustment based on the settlement
     * date relative to the due date.
     */
    @Transactional
    public void onDebtSettled(Debt debt) {
        User debtor = debt.getDebtor();
        LocalDate settledDate = debt.getSettledAt() != null
                ? debt.getSettledAt().atZone(ZoneOffset.UTC).toLocalDate()
                : LocalDate.now();

        apply(debtor, FULL_SETTLEMENT, "Full settlement of debt #" + debt.getId());

        if (settledDate.isBefore(debt.getDueDate())) {
            apply(debtor, EARLY, "Early settlement of debt #" + debt.getId());
        } else if (settledDate.isEqual(debt.getDueDate())) {
            apply(debtor, ON_TIME, "On-time settlement of debt #" + debt.getId());
        } else {
            apply(debtor, LATE, "Late settlement of debt #" + debt.getId());
        }
    }

    /**
     * Called by the overdue sweep for a debt still unpaid 30+ days past due.
     */
    @Transactional
    public void onOverdue(Debt debt) {
        apply(debt.getDebtor(), OVERDUE, "Debt #" + debt.getId() + " overdue 30+ days");
    }

    private void apply(User user, int delta, String reason) {
        int updated = clamp(user.getTrustScore() + delta);
        user.setTrustScore(updated);
        userRepository.save(user);
        eventRepository.save(new TrustScoreEvent(user, delta, reason, updated));
    }

    private int clamp(int score) {
        return Math.max(properties.getMinScore(), Math.min(properties.getMaxScore(), score));
    }
}
