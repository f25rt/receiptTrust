package com.receipttrust.assignment;

import com.receipttrust.receipt.Receipt;
import com.receipttrust.receipt.ReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemAssignmentRepository extends JpaRepository<ItemAssignment, Long> {

    List<ItemAssignment> findByReceiptItem(ReceiptItem receiptItem);

    @Query("""
            select a from ItemAssignment a
            where a.receiptItem.receipt = :receipt
            """)
    List<ItemAssignment> findByReceipt(@Param("receipt") Receipt receipt);

    /** Unconfirmed assignments on a receipt (block finalize while any remain). */
    @Query("""
            select a from ItemAssignment a
            where a.receiptItem.receipt = :receipt
              and a.confirmed = false
            """)
    List<ItemAssignment> findUnconfirmedByReceipt(@Param("receipt") Receipt receipt);

    /** Assignments awaiting the given user's confirmation, on non-finalized receipts. */
    @Query("""
            select a from ItemAssignment a
            where a.assignee.id = :userId
              and a.confirmed = false
              and a.receiptItem.receipt.finalized = false
            order by a.createdAt desc
            """)
    List<ItemAssignment> findPendingForAssignee(@Param("userId") Long userId);
}
