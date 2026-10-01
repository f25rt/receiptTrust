package com.receipttrust.debt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebtCommentRepository extends JpaRepository<DebtComment, Long> {

    List<DebtComment> findByDebtOrderByCreatedAtAsc(Debt debt);
}
