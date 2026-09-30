package com.receipttrust.user;

import com.receipttrust.debt.Debt;
import com.receipttrust.debt.DebtRepository;
import com.receipttrust.debt.DebtStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
public class ProfileMetricsService {

    private final DebtRepository debtRepository;

    public ProfileMetricsService(DebtRepository debtRepository) {
        this.debtRepository = debtRepository;
    }

    @Transactional(readOnly = true)
    public long debtsSettled(User user) {
        return debtRepository.countByDebtorAndStatus(user, DebtStatus.SETTLED);
    }

    @Transactional(readOnly = true)
    public long currentDebts(User user) {
        return debtRepository.countByDebtorAndStatus(user, DebtStatus.ACTIVE);
    }

    /**
     * Average days between debt creation and settlement over settled debts.
     * Computed in Java for database portability. Returns null when none settled.
     */
    @Transactional(readOnly = true)
    public Double averageRepaymentDays(User user) {
        List<Debt> settled = debtRepository.findByDebtorAndStatus(user, DebtStatus.SETTLED);
        List<Debt> withTimestamps = settled.stream()
                .filter(d -> d.getSettledAt() != null && d.getCreatedAt() != null)
                .toList();
        if (withTimestamps.isEmpty()) {
            return null;
        }
        double totalDays = withTimestamps.stream()
                .mapToDouble(d -> {
                    long seconds = Duration.between(d.getCreatedAt(), d.getSettledAt()).getSeconds();
                    return seconds / 86_400.0;
                })
                .sum();
        return totalDays / withTimestamps.size();
    }
}
