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
}
