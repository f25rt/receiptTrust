package com.receipttrust.payment;

import com.receipttrust.debt.Debt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByDebtOrderByCreatedAtAsc(Debt debt);

    List<Payment> findByDebtAndStatusOrderByDecidedAtAsc(Debt debt, PaymentStatus status);
}
