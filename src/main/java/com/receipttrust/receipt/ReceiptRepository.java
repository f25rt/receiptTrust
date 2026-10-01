package com.receipttrust.receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    /** Receipts that still have a stored image and were created before the cutoff. */
    @Query("""
            select r from Receipt r
            where r.imagePath is not null
              and r.createdAt < :cutoff
            """)
    List<Receipt> findWithImageOlderThan(@Param("cutoff") Instant cutoff);
}
