package com.HyperLocalSystem.app.payment.repository;

import com.HyperLocalSystem.app.payment.entity.WalletEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletEntryRepository extends JpaRepository<WalletEntry, UUID> {

    @Query("""
            select coalesce(sum(case when e.type = com.HyperLocalSystem.app.payment.entity.WalletTransactionType.CREDIT
                                     then e.amount else -e.amount end), 0)
            from WalletEntry e where e.customerId = :customerId
            """)
    BigDecimal balanceOf(@Param("customerId") UUID customerId);

    Page<WalletEntry> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    boolean existsByReference(String reference);
}
