package com.receipttrust.debt;

import com.receipttrust.receipt.Receipt;
import com.receipttrust.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    List<Debt> findByCreditor(User creditor);

    List<Debt> findByDebtor(User debtor);

    boolean existsByReceipt(Receipt receipt);

    long countByDebtorAndStatus(User debtor, DebtStatus status);

    @Query("""
            select d from Debt d
            where d.status = com.receipttrust.debt.DebtStatus.ACTIVE
              and d.dueDate <= :cutoff
              and d.overduePenalized = false
            """)
    List<Debt> findOverdueUnpenalized(@Param("cutoff") LocalDate cutoff);

    List<Debt> findByDebtorAndStatus(User debtor, DebtStatus status);
}
