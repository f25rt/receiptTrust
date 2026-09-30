package com.receipttrust.trust;

import com.receipttrust.debt.Debt;
import com.receipttrust.debt.DebtRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Applies the overdue penalty once to any active debt that is 30+ days past its
 * due date. The {@code overduePenalized} flag prevents double-charging.
 */
@Component
public class OverdueSweepJob {

    private final DebtRepository debtRepository;
    private final TrustScoreService trustScoreService;

    public OverdueSweepJob(DebtRepository debtRepository, TrustScoreService trustScoreService) {
        this.debtRepository = debtRepository;
        this.trustScoreService = trustScoreService;
    }

    // Daily at 02:00 server time.
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void sweep() {
        LocalDate cutoff = LocalDate.now().minusDays(30);
        List<Debt> overdue = debtRepository.findOverdueUnpenalized(cutoff);
        for (Debt debt : overdue) {
            trustScoreService.onOverdue(debt);
            debt.setOverduePenalized(true);
            debtRepository.save(debt);
        }
    }
}
