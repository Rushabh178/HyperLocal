package com.HyperLocalSystem.app.payment.repository;

import com.HyperLocalSystem.app.payment.entity.MerchantSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantSettlementRepository extends JpaRepository<MerchantSettlement, UUID> {

    boolean existsByMerchantIdAndOrderId(UUID merchantId, UUID orderId);

    Optional<MerchantSettlement> findByMerchantIdAndOrderId(UUID merchantId, UUID orderId);

    /** {@code [count, sum]} of settlements within [from, to). */
    @Query("""
            select count(s), coalesce(sum(s.amount), 0) from MerchantSettlement s
            where s.merchantId = :merchantId and s.createdAt >= :from and s.createdAt < :to
            """)
    List<Object[]> totals(@Param("merchantId") UUID merchantId, @Param("from") Instant from, @Param("to") Instant to);
}
