package com.receipttrust.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceiptItemRepository extends JpaRepository<ReceiptItem, Long> {

    List<ReceiptItem> findByReceipt(Receipt receipt);
}
